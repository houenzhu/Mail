package com.zhe.mall.order.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.ProductApi;
import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.PayCallbackRequest;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.api.dto.RefundStage;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.cache.IdempotentGuard;
import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.mq.MessageTopics;
import com.zhe.mall.common.mq.OutboxService;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.common.util.SnowflakeIdGenerator;
import com.zhe.mall.order.dto.OrderCreateRequest;
import com.zhe.mall.order.dto.OrderCreateVO;
import com.zhe.mall.order.dto.OrderDTO;
import com.zhe.mall.order.dto.OrderDetailVO;
import com.zhe.mall.order.dto.OrderItemDTO;
import com.zhe.mall.order.dto.OrderItemRequest;
import com.zhe.mall.order.dto.OrderPageQuery;
import com.zhe.mall.order.dto.OrderStatusLogDTO;
import com.zhe.mall.order.entity.Order;
import com.zhe.mall.order.entity.OrderItem;
import com.zhe.mall.order.entity.OrderStatusLog;
import com.zhe.mall.order.enums.OrderOperator;
import com.zhe.mall.order.enums.OrderStatus;
import com.zhe.mall.order.mapper.OrderItemMapper;
import com.zhe.mall.order.mapper.OrderMapper;
import com.zhe.mall.order.mapper.OrderStatusLogMapper;
import com.zhe.mall.order.statemachine.OrderStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 建单时无前序状态，落 order_status_log.from_status = 0 */
    private static final int NO_PREVIOUS_STATUS = 0;

    private static final int SKU_STATUS_ON_SHELF = 1;

    private static final int DEFAULT_PAY_TYPE = 0;

    private static final String TIMEOUT_CANCEL_REASON = "支付超时自动取消";

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final OrderStatusLogMapper orderStatusLogMapper;

    private final ProductApi productApi;

    private final InventoryApi inventoryApi;

    private final long payTimeoutMinutes;

    private final IdempotentGuard idempotentGuard;

    private final long idemTtlHours;

    private final long idemPendingTtlSeconds;

    /** Outbox 写入器：消息与业务写同事务落库（19.6 / 20.3）。 */
    private final OutboxService outboxService;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        OrderStatusLogMapper orderStatusLogMapper,
                        ProductApi productApi,
                        InventoryApi inventoryApi,
                        IdempotentGuard idempotentGuard,
                        OutboxService outboxService,
                        @Value("${mall.order.pay-timeout-minutes:30}") long payTimeoutMinutes,
                        @Value("${mall.idem.ttl-hours:24}") long idemTtlHours,
                        @Value("${mall.idem.pending-ttl-seconds:60}") long idemPendingTtlSeconds) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
        this.productApi = productApi;
        this.inventoryApi = inventoryApi;
        this.idempotentGuard = idempotentGuard;
        this.outboxService = outboxService;
        this.payTimeoutMinutes = payTimeoutMinutes;
        this.idemTtlHours = idemTtlHours;
        this.idemPendingTtlSeconds = idemPendingTtlSeconds;
    }

    /**
     * 下单主链路：唯一索引幂等 → 商品快照 → 订单与明细落库 → 状态日志 → 库存扣减（失败同步补偿）。
     * 跨服务写操作不在本地事务内，任一扣减失败都会回滚已成功的扣减并抛异常触发本地回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO create(OrderCreateRequest request, Long userId) {
        // 第一层：Redis 幂等标记（19.9 / 27.7）。它只负责"少做无用功"，正确性仍由 DB 唯一索引保证
        String idemKey = MallCacheKeys.orderIdempotent(userId, request.requestId());
        boolean firstAttempt = idempotentGuard.claim(idemKey, MallCacheKeys.PENDING_VALUE,
                Duration.ofSeconds(idemPendingTtlSeconds));
        if (!firstAttempt) {
            String marker = idempotentGuard.current(idemKey);
            if (MallCacheKeys.PENDING_VALUE.equals(marker)) {
                throw new BusinessException(ResultCode.DUPLICATE_REQUEST, "重复请求处理中，请稍后重试");
            }
            if (marker != null) {
                Order marked = orderMapper.selectByUserRequest(userId, request.requestId());
                if (marked != null) {
                    log.info("下单幂等命中(Redis 标记): userId={}, requestId={}, orderNo={}",
                            userId, request.requestId(), marked.getOrderNo());
                    return toCreateVO(marked, true);
                }
            }
            // 标记被淘汰 / Redis 不可用：继续走原有的 DB 唯一索引幂等路径
        } else {
            releaseIdemMarkerOnRollback(idemKey);
        }

        // 第二层：DB 唯一索引（uk_user_request）
        Order existing = orderMapper.selectByUserRequest(userId, request.requestId());
        if (existing != null) {
            log.info("下单幂等命中: userId={}, requestId={}, orderNo={}",
                    userId, request.requestId(), existing.getOrderNo());
            idempotentGuard.complete(idemKey, existing.getOrderNo(), Duration.ofHours(idemTtlHours));
            return toCreateVO(existing, true);
        }

        String orderNo = SnowflakeIdGenerator.nextIdStr();
        List<OrderItem> items = buildItems(orderNo, request, userId);
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal payAmount = totalAmount.subtract(discountAmount);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setRequestId(request.requestId());
        order.setStatus(OrderStatus.CREATED.code());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(payAmount);
        order.setCouponId(request.couponId() == null ? 0L : request.couponId());
        order.setPayType(request.payType() == null ? DEFAULT_PAY_TYPE : request.payType());
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        order.setRemark(request.remark());
        order.setDeleted(0);
        order.setExpireAt(LocalDateTime.now().plusMinutes(payTimeoutMinutes));

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            Order duplicated = orderMapper.selectByUserRequest(userId, request.requestId());
            if (duplicated == null) {
                throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "订单创建冲突，请稍后重试");
            }
            log.info("下单并发幂等命中: userId={}, requestId={}, orderNo={}",
                    userId, request.requestId(), duplicated.getOrderNo());
            return toCreateVO(duplicated, true);
        }
        orderItemMapper.insertBatch(items);
        writeStatusLog(orderNo, NO_PREVIOUS_STATUS, OrderStatus.CREATED, OrderOperator.USER, "创建订单");

        deductInventory(orderNo, items);
        publishOrderCreated(order, items);
        publishOrderTimeout(order, userId);
        idempotentGuard.complete(idemKey, orderNo, Duration.ofHours(idemTtlHours));
        log.info("下单成功: orderNo={}, userId={}, skuCount={}, payAmount={}, expireAt={}",
                orderNo, userId, items.size(), payAmount, order.getExpireAt());
        return toCreateVO(order, false);
    }

    /**
     * 发布 {@code TOPIC_ORDER_CREATED}（19.6）：驱动券核销等异步后续动作。
     *
     * <p>与订单落库同一个事务 —— 订单成立则事件必达；订单回滚则事件一起消失。
     */
    private void publishOrderCreated(Order order, List<OrderItem> items) {
        outboxService.record(
                MessageTopics.ORDER_CREATED,
                MessageTopics.TAG_CREATED,
                order.getOrderNo(),
                "ORDER_CREATED",
                order.getOrderNo(),
                new MessageEvents.OrderCreatedEvent(
                        order.getOrderNo(),
                        order.getUserId(),
                        order.getTotalAmount(),
                        order.getPayAmount(),
                        toEventItems(items),
                        order.getCouponId()));
    }

    /**
     * 发布 {@code TOPIC_ORDER_TIMEOUT}（19.6）：延时 30 分钟触发。
     *
     * <p>这是超时关单的<b>主路径</b>，精确到下单时刻 + 30min；{@code OrderTimeoutTask}
     * 的分钟级扫描降级为兜底（两者的取消 CAS 相同，不会重复取消）。
     *
     * <p>延时级别固定 16 = 30 分钟，与 {@code mall.order.pay-timeout-minutes} 独立 ——
     * 若把超时时间改成非 30 分钟，需要同步调整 delayLevel 或用兜底扫描兜住（见 19.6 备注）。
     */
    private void publishOrderTimeout(Order order, Long userId) {
        outboxService.record(
                MessageTopics.ORDER_TIMEOUT,
                MessageTopics.TAG_TIMEOUT,
                order.getOrderNo(),
                "ORDER_TIMEOUT",
                order.getOrderNo(),
                new MessageEvents.OrderTimeoutEvent(
                        order.getOrderNo(),
                        userId,
                        order.getExpireAt() == null ? null : order.getExpireAt().toString()),
                OutboxService.DELAY_LEVEL_30_MINUTES);
    }

    private List<MessageEvents.Item> toEventItems(List<OrderItem> items) {
        return items.stream()
                .map(item -> new MessageEvents.Item(item.getSkuId(), item.getQty(), item.getPrice()))
                .toList();
    }

    /**
     * 事务回滚时释放幂等标记：否则一次"库存不足"会把同一个 requestId 锁在 PENDING 状态，
     * 使用者改小数量重试也会被误判为重复请求（27.7）。
     */
    private void releaseIdemMarkerOnRollback(String idemKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    idempotentGuard.release(idemKey);
                }
            }
        });
    }

    public OrderDetailVO detail(String orderNo, Long userId) {
        Order order = requireOrder(orderNo);
        requireOwnership(order, userId);
        List<OrderItemDTO> items = orderItemMapper.selectByOrderNo(orderNo).stream()
                .map(this::toItemDTO)
                .toList();
        List<OrderStatusLogDTO> logs = orderStatusLogMapper.selectByOrderNo(orderNo).stream()
                .map(this::toLogDTO)
                .toList();
        return new OrderDetailVO(toDTO(order), items, logs);
    }

    public PageResult<OrderDTO> page(OrderPageQuery query) {
        long total = orderMapper.countPage(query);
        if (total == 0) {
            return PageResult.empty(query.pageNo(), query.pageSize());
        }
        List<OrderDTO> list = orderMapper.selectPage(query, query.offset(), query.pageSize()).stream()
                .map(this::toDTO)
                .toList();
        return PageResult.of(total, query.pageNo(), query.pageSize(), list);
    }

    /**
     * 用户取消订单：先校验归属，再走通用取消流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO cancel(String orderNo, String reason, Long userId) {
        Order order = requireOrder(orderNo);
        requireOwnership(order, userId);
        return doCancel(order, reason, OrderOperator.USER);
    }

    /**
     * 系统取消订单（超时关单）：无请求上下文，不做归属校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO cancelBySystem(String orderNo, String reason) {
        return doCancel(requireOrder(orderNo), reason, OrderOperator.SYSTEM);
    }

    /**
     * 取消订单：CAS 抢占状态 → 状态日志 → 库存回滚。重复取消返回当前订单（幂等）。
     */
    private OrderDTO doCancel(Order order, String reason, OrderOperator operator) {
        String orderNo = order.getOrderNo();
        OrderStatus current = OrderStatus.of(order.getStatus());
        if (current == OrderStatus.CANCELLED) {
            log.info("取消订单幂等命中: orderNo={}", orderNo);
            return toDTO(order);
        }
        OrderStateMachine.check(current, OrderStatus.CANCELLED);

        String cancelReason = reason == null || reason.isBlank()
                ? (operator == OrderOperator.SYSTEM ? TIMEOUT_CANCEL_REASON : "用户取消")
                : reason;
        int affected = orderMapper.markCancelled(orderNo, current.code(), OrderStatus.CANCELLED.code(),
                LocalDateTime.now(), cancelReason);
        if (affected == 0) {
            Order latest = orderMapper.selectByOrderNo(orderNo);
            if (latest != null && Objects.equals(latest.getStatus(), OrderStatus.CANCELLED.code())) {
                log.info("取消订单并发幂等命中: orderNo={}", orderNo);
                return toDTO(latest);
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL, "取消订单失败：订单状态已变更，请刷新后重试");
        }
        writeStatusLog(orderNo, current.code(), OrderStatus.CANCELLED, operator, cancelReason);
        // 先落地可靠事件（与取消同事务），再做同步回滚争取"立刻可再下单"
        publishStockRollback(order, cancelReason);
        rollbackInventory(orderNo);
        log.info("取消订单成功: orderNo={}, operator={}, reason={}", orderNo, operator.code(), cancelReason);
        return toDTO(requireOrder(orderNo));
    }

    /**
     * 发布 {@code TOPIC_STOCK_ROLLBACK}（19.6），并把同步回滚降级为「尽力而为」。
     *
     * <p><b>为什么两者都保留</b>：
     * <ol>
     *   <li>同步调用让库存<b>立刻</b>归还，用户能马上再次下单（体验）；</li>
     *   <li>MQ 事件是<b>可靠</b>的那一份：同步调用失败时不再只留一行 WARN 日志，
     *       而是由补发任务重投直到成功。两处最终都落到 {@code inventory_record} 的
     *       {@code uk_order_sku_type} 唯一键上，所以重复执行是安全的（19.6）。</li>
     * </ol>
     *
     * <p>事件与取消动作同事务：取消成立则回滚事件必达，闭合
     * 「订单已取消但库存未归还」的缺口（20.4）。
     */
    private void publishStockRollback(Order order, String reason) {
        List<OrderItem> items = orderItemMapper.selectByOrderNo(order.getOrderNo());
        if (items.isEmpty()) {
            return;
        }
        outboxService.record(
                MessageTopics.STOCK_ROLLBACK,
                MessageTopics.TAG_ROLLBACK,
                order.getOrderNo(),
                "STOCK_ROLLBACK",
                order.getOrderNo(),
                new MessageEvents.StockRollbackEvent(
                        order.getOrderNo(),
                        order.getUserId(),
                        toEventItems(items),
                        reason));
    }

    /**
     * 订单支付视图：支付侧发起支付 / 申请退款前的校验入口（内部接口，调用方已校验内部令牌）。
     *
     * <p>不做归属校验——归属由 mall-pay 依据网关身份判定后再调本接口，避免两处各判一次口径不一致。
     */
    public OrderPayView payView(String orderNo) {
        return toPayView(requireOrder(orderNo));
    }

    /**
     * 支付回调：仅 CREATED 可流转到 PAID，重复回调直接返回当前订单（幂等）。
     *
     * <p>只由 mall-pay 经内部接口调用（ADR-022），公开路径已下线。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPayView payCallback(String orderNo, PayCallbackRequest request) {
        Order order = requireOrder(orderNo);
        checkPayAmount(order, request.amount());
        OrderStatus current = OrderStatus.of(order.getStatus());
        if (current != OrderStatus.CREATED) {
            log.info("支付回调幂等命中: orderNo={}, status={}", orderNo, current.label());
            return toPayView(order);
        }
        OrderStateMachine.check(current, OrderStatus.PAID);

        int affected = orderMapper.markPaid(orderNo, current.code(), OrderStatus.PAID.code(),
                request.payType() == null ? DEFAULT_PAY_TYPE : request.payType(), LocalDateTime.now());
        if (affected == 0) {
            log.info("支付回调并发幂等命中: orderNo={}", orderNo);
            return toPayView(requireOrder(orderNo));
        }
        writeStatusLog(orderNo, current.code(), OrderStatus.PAID, OrderOperator.PAY,
                request.paymentNo() == null || request.paymentNo().isBlank()
                        ? "支付回调" : "支付回调: " + request.paymentNo());
        log.info("支付回调处理成功: orderNo={}, paymentNo={}", orderNo, request.paymentNo());
        return toPayView(requireOrder(orderNo));
    }

    /**
     * 退款阶段回调（28.5）：订单侧只认三个阶段，不复制支付侧的退款状态机。
     *
     * <p>幂等口径：<b>已经到达目标状态（或更终态）即视为重复投递</b>，直接返回当前订单；
     * 真正的非法流转（如已退款后被驳回）才抛 34002，由支付侧记 ERROR 并进对账清单。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPayView refundCallback(String orderNo, RefundCallbackRequest request) {
        if (request.stage() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "退款阶段不能为空");
        }
        Order order = requireOrder(orderNo);
        OrderStatus current = OrderStatus.of(order.getStatus());
        OrderStatus target = refundTarget(request.stage());
        if (isRefundSettled(current, request.stage())) {
            log.info("退款回调幂等命中: orderNo={}, stage={}, status={}",
                    orderNo, request.stage(), current.label());
            return toPayView(order);
        }
        OrderStateMachine.check(current, target);

        int affected = orderMapper.updateStatus(orderNo, current.code(), target.code());
        if (affected == 0) {
            Order latest = requireOrder(orderNo);
            if (isRefundSettled(OrderStatus.of(latest.getStatus()), request.stage())) {
                log.info("退款回调并发幂等命中: orderNo={}, stage={}", orderNo, request.stage());
                return toPayView(latest);
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL, "退款回调失败：订单状态已变更，请重试");
        }
        writeStatusLog(orderNo, current.code(), target, OrderOperator.PAY,
                refundRemark(request));
        log.info("退款回调处理成功: orderNo={}, refundNo={}, stage={}, {} -> {}",
                orderNo, request.refundNo(), request.stage(), current.code(), target.code());
        return toPayView(requireOrder(orderNo));
    }

    /** 退款阶段 → 目标状态（与 28.5 联动矩阵一一对应）。 */
    private OrderStatus refundTarget(RefundStage stage) {
        return switch (stage) {
            case REFUNDING -> OrderStatus.REFUNDING;
            case REFUNDED -> OrderStatus.REFUNDED;
            case REJECTED -> OrderStatus.PAID;
        };
    }

    /**
     * 该阶段是否已经"无需再处理"：重复投递（含乱序到达）返回当前订单，而不是报错。
     *
     * <p>{@code REFUNDING} 允许当前已是 REFUNDED——退款成功回调先到、受理回调后到属正常乱序。
     */
    private boolean isRefundSettled(OrderStatus current, RefundStage stage) {
        return switch (stage) {
            case REFUNDING -> current == OrderStatus.REFUNDING || current == OrderStatus.REFUNDED;
            case REFUNDED -> current == OrderStatus.REFUNDED;
            case REJECTED -> current == OrderStatus.PAID;
        };
    }

    private String refundRemark(RefundCallbackRequest request) {
        String refundNo = request.refundNo() == null || request.refundNo().isBlank()
                ? "-" : request.refundNo();
        String stageLabel = switch (request.stage()) {
            case REFUNDING -> "退款受理";
            case REFUNDED -> "退款成功";
            case REJECTED -> "退款驳回";
        };
        return request.reason() == null || request.reason().isBlank()
                ? stageLabel + ": " + refundNo
                : stageLabel + ": " + refundNo + "（" + request.reason() + "）";
    }

    private void checkPayAmount(Order order, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        if (order.getPayAmount().compareTo(amount) != 0) {
            throw new BusinessException(ResultCode.PAY_AMOUNT_MISMATCH,
                    "回调金额与订单应付金额不一致: " + amount + " != " + order.getPayAmount());
        }
    }

    /**
     * 归属校验：只有本人或客服/运营/管理员可操作该订单（见需求文档 21.3）。
     */
    private void requireOwnership(Order order, Long userId) {
        if (userId != null && userId.equals(order.getUserId())) {
            return;
        }
        CurrentUser currentUser = UserContext.get();
        if (currentUser != null && currentUser.isStaff()) {
            return;
        }
        log.warn("订单越权访问被拒绝: orderNo={}, ownerUserId={}, requestUserId={}",
                order.getOrderNo(), order.getUserId(), userId);
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该订单");
    }

    /**
     * 超时兜底扫描：仅返回候选订单号，具体取消失败不影响其它订单（逐单独立事务）。
     */
    public List<String> findTimeoutOrderNos(int limit) {
        return orderMapper.selectTimeoutCandidates(limit).stream()
                .map(Order::getOrderNo)
                .toList();
    }

    public String timeoutCancelReason() {
        return TIMEOUT_CANCEL_REASON;
    }

    private List<OrderItem> buildItems(String orderNo, OrderCreateRequest request, Long userId) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "订单明细不能为空");
        }
        Map<Long, Integer> qtyBySkuId = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            if (item.skuId() == null || item.qty() == null || item.qty() <= 0) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "skuId 与购买数量必须合法");
            }
            qtyBySkuId.merge(item.skuId(), item.qty(), Integer::sum);
        }

        List<OrderItem> items = new ArrayList<>(qtyBySkuId.size());
        for (Map.Entry<Long, Integer> entry : qtyBySkuId.entrySet()) {
            SkuDTO sku = requireSku(entry.getKey());
            if (!Objects.equals(sku.status(), SKU_STATUS_ON_SHELF)) {
                throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF, "SKU 已下架，无法下单: " + entry.getKey());
            }
            OrderItem item = new OrderItem();
            item.setId(SnowflakeIdGenerator.nextId());
            item.setOrderNo(orderNo);
            item.setUserId(userId);
            item.setSkuId(sku.id());
            item.setProductId(sku.productId() == null ? 0L : sku.productId());
            item.setProductName(sku.name());
            item.setSkuSpec(sku.specJson());
            item.setPrice(sku.price());
            item.setQty(entry.getValue());
            item.setTotalAmount(sku.price().multiply(BigDecimal.valueOf(entry.getValue())));
            items.add(item);
        }
        return items;
    }

    private void deductInventory(String orderNo, List<OrderItem> items) {
        List<OrderItem> deducted = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            DeductResult result;
            try {
                result = requireDeductResult(
                        inventoryApi.deduct(new DeductRequest(orderNo, item.getSkuId(), item.getQty())));
            } catch (RuntimeException e) {
                compensateDeduct(orderNo, deducted);
                throw e;
            }
            if (!result.success()) {
                compensateDeduct(orderNo, deducted);
                throw new BusinessException(result.code(), "库存扣减失败: " + result.message());
            }
            deducted.add(item);
        }
    }

    private DeductResult requireDeductResult(Result<DeductResult> result) {
        if (result == null) {
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "库存服务无响应");
        }
        if (!result.isSuccess() || result.data() == null) {
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "库存扣减失败: " + result.message());
        }
        return result.data();
    }

    /**
     * 同步补偿：本地事务尚未提交，先把已成功的扣减回滚，避免出现"订单没落库、库存被扣"。
     * 补偿本身失败只告警，后续由 MQ 补偿与对账兜底（第三阶段）。
     */
    private void compensateDeduct(String orderNo, List<OrderItem> deducted) {
        for (OrderItem item : deducted) {
            try {
                Result<DeductResult> result = inventoryApi.rollback(
                        new DeductRequest(orderNo, item.getSkuId(), item.getQty()));
                log.warn("库存补偿回滚已提交: orderNo={}, skuId={}, qty={}, success={}",
                        orderNo, item.getSkuId(), item.getQty(), result != null && result.isSuccess());
            } catch (Exception e) {
                log.error("库存补偿回滚失败，需对账兜底: orderNo={}, skuId={}, qty={}",
                        orderNo, item.getSkuId(), item.getQty(), e);
            }
        }
    }

    private void rollbackInventory(String orderNo) {
        for (OrderItem item : orderItemMapper.selectByOrderNo(orderNo)) {
            try {
                Result<DeductResult> result = inventoryApi.rollback(
                        new DeductRequest(orderNo, item.getSkuId(), item.getQty()));
                if (result == null || !result.isSuccess()) {
                    log.warn("库存回滚未成功: orderNo={}, skuId={}, message={}",
                            orderNo, item.getSkuId(), result == null ? "无响应" : result.message());
                }
            } catch (Exception e) {
                log.error("库存回滚异常，需 MQ 补偿兜底: orderNo={}, skuId={}", orderNo, item.getSkuId(), e);
            }
        }
    }

    private SkuDTO requireSku(Long skuId) {
        Result<SkuDTO> result = productApi.getSkuById(skuId);
        if (result == null || !result.isSuccess() || result.data() == null) {
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "商品服务查询 SKU 失败: " + skuId);
        }
        return result.data();
    }

    private Order requireOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "orderNo 不能为空");
        }
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        return order;
    }

    private void writeStatusLog(String orderNo, int fromStatus, OrderStatus toStatus,
                                OrderOperator operator, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setId(SnowflakeIdGenerator.nextId());
        statusLog.setOrderNo(orderNo);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus.code());
        statusLog.setOperator(operator.code());
        statusLog.setRemark(remark);
        orderStatusLogMapper.insert(statusLog);
    }

    private OrderCreateVO toCreateVO(Order order, boolean idempotentReplay) {
        return new OrderCreateVO(order.getOrderNo(), order.getStatus(),
                OrderStatus.of(order.getStatus()).label(), order.getPayAmount(),
                order.getExpireAt(), idempotentReplay);
    }

    private OrderPayView toPayView(Order order) {
        return new OrderPayView(order.getOrderNo(), order.getUserId(), order.getStatus(),
                OrderStatus.of(order.getStatus()).label(), order.getPayAmount(), order.getPayType(),
                order.getExpireAt());
    }

    private OrderDTO toDTO(Order order) {
        return new OrderDTO(order.getOrderNo(), order.getUserId(), order.getStatus(),
                OrderStatus.of(order.getStatus()).label(), order.getTotalAmount(), order.getDiscountAmount(),
                order.getPayAmount(), order.getCouponId(), order.getPayType(), order.getCancelReason(),
                order.getExpireAt(), order.getPaidAt(), order.getCreatedAt());
    }

    private OrderItemDTO toItemDTO(OrderItem item) {
        return new OrderItemDTO(item.getId(), item.getSkuId(), item.getProductId(), item.getProductName(),
                item.getSkuSpec(), item.getPrice(), item.getQty(), item.getTotalAmount());
    }

    private OrderStatusLogDTO toLogDTO(OrderStatusLog statusLog) {
        return new OrderStatusLogDTO(statusLog.getFromStatus(), statusLog.getToStatus(),
                statusLog.getOperator(), statusLog.getRemark(), statusLog.getCreatedAt());
    }
}
