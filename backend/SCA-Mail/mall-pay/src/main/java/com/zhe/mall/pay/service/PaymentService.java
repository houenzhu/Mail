package com.zhe.mall.pay.service;

import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.cache.IdempotentGuard;
import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.mq.MessageTopics;
import com.zhe.mall.common.mq.OutboxService;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.common.util.SnowflakeIdGenerator;
import com.zhe.mall.pay.channel.ChannelPayResult;
import com.zhe.mall.pay.channel.ChannelQueryResult;
import com.zhe.mall.pay.channel.PayChannel;
import com.zhe.mall.pay.channel.PaySigner;
import com.zhe.mall.pay.config.PayProperties;
import com.zhe.mall.pay.dto.CallbackRequest;
import com.zhe.mall.pay.dto.CallbackResultVO;
import com.zhe.mall.pay.dto.ChannelParams;
import com.zhe.mall.pay.dto.PayCreateRequest;
import com.zhe.mall.pay.dto.PayCreateVO;
import com.zhe.mall.pay.dto.PaymentVO;
import com.zhe.mall.pay.entity.Payment;
import com.zhe.mall.pay.entity.PaymentCallbackLog;
import com.zhe.mall.pay.enums.CallbackEventType;
import com.zhe.mall.pay.enums.CallbackProcessResult;
import com.zhe.mall.pay.enums.PaymentStatus;
import com.zhe.mall.pay.mapper.PaymentCallbackLogMapper;
import com.zhe.mall.pay.mapper.PaymentMapper;
import com.zhe.mall.pay.support.OrderClientWrapper;
import com.zhe.mall.pay.support.PayChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 支付单主流程：发起支付、回调处理、主动查询对齐、超时关闭（28.4 / 28.5 / 28.6 / 28.8）。
 *
 * <p><b>为什么关键方法刻意不加 {@code @Transactional}</b>：
 * <ol>
 *   <li>28.6 纪律 1 要求"先落流水再做业务"，回调失败返回非 200 时流水<b>必须留存</b>；
 *       如果包在一个事务里，抛异常会把留痕一起回滚，审计直接缺口（26.8 第 2 条同类踩坑）；</li>
 *   <li>发起支付要调用渠道与订单服务（跨进程），事务里做远程调用会长时间占连接；</li>
 *   <li>状态变更全部走<b>单条 CAS</b>，本身即原子，不需要外层事务兜底（27.2 ADR-014 的同类口径）。</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** 支付侧只认这两个回调状态（28.6 纪律 2） */
    private static final String CALLBACK_STATUS_SUCCESS = "SUCCESS";

    private static final String CALLBACK_STATUS_FAIL = "FAIL";

    private static final String PAYMENT_NO_PREFIX = "P";

    private static final String RAW_BODY_ELLIPSIS = "…";

    private static final int RAW_BODY_MAX = 1024;

    /** 订单可发起支付的状态：10 待支付 */
    private static final int ORDER_STATUS_CREATED = 10;

    private final PaymentMapper paymentMapper;

    private final PaymentCallbackLogMapper callbackLogMapper;

    private final OrderClientWrapper orderClient;

    private final PayChannelRegistry channelRegistry;

    private final IdempotentGuard idempotentGuard;

    private final PayProperties properties;

    private final long idemTtlHours;

    private final long idemPendingTtlSeconds;

    /** Outbox 写入器：订单通知失败时落事件兜底（第三阶段第 7 项）。 */
    private final OutboxService outboxService;

    public PaymentService(PaymentMapper paymentMapper,
                          PaymentCallbackLogMapper callbackLogMapper,
                          OrderClientWrapper orderClient,
                          PayChannelRegistry channelRegistry,
                          IdempotentGuard idempotentGuard,
                          PayProperties properties,
                          OutboxService outboxService,
                          @Value("${mall.idem.ttl-hours:24}") long idemTtlHours,
                          @Value("${mall.idem.pending-ttl-seconds:60}") long idemPendingTtlSeconds) {
        this.paymentMapper = paymentMapper;
        this.callbackLogMapper = callbackLogMapper;
        this.orderClient = orderClient;
        this.channelRegistry = channelRegistry;
        this.idempotentGuard = idempotentGuard;
        this.properties = properties;
        this.outboxService = outboxService;
        this.idemTtlHours = idemTtlHours;
        this.idemPendingTtlSeconds = idemPendingTtlSeconds;
    }

    // ------------------------------------------------------------------ 发起支付

    /**
     * 发起支付：幂等建单 → 关闭同订单旧单 → 渠道下单 → 返回渠道参数（28.4 / 28.5）。
     */
    public PayCreateVO create(PayCreateRequest request, Long userId) {
        String orderNo = request.orderNo();
        // 第一层：DB 唯一索引（权威）。命中即直接回放，连渠道都不用调
        Payment existing = paymentMapper.selectByOrderRequest(orderNo, request.requestId());
        if (existing != null) {
            log.info("发起支付幂等命中（DB）: orderNo={}, requestId={}, paymentNo={}",
                    orderNo, request.requestId(), existing.getPaymentNo());
            return toCreateVO(existing, true);
        }

        // 第二层：Redis 幂等标记（拦截层）。Redis 不可用时整层跳过，正确性仍由上面那层保证（28.11 #18）
        String idemKey = MallCacheKeys.payIdempotent(userId, request.requestId());
        boolean claimed = false;
        if (idempotentGuard.available()) {
            claimed = idempotentGuard.claim(idemKey, MallCacheKeys.PENDING_VALUE,
                    Duration.ofSeconds(idemPendingTtlSeconds));
            if (!claimed) {
                String value = idempotentGuard.current(idemKey);
                if (value != null && !MallCacheKeys.PENDING_VALUE.equals(value)) {
                    Payment replay = paymentMapper.selectByPaymentNo(value);
                    if (replay != null) {
                        log.info("发起支付幂等命中（Redis）: key={}, paymentNo={}", idemKey, value);
                        return toCreateVO(replay, true);
                    }
                } else if (value != null) {
                    throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "支付请求处理中，请稍后重试");
                }
                log.warn("支付幂等标记不可读，降级为 DB 唯一索引兜底: key={}", idemKey);
            }
        } else {
            log.warn("Redis 不可用，发起支付降级为 DB 唯一索引兜底: orderNo={}", orderNo);
        }

        try {
            PayCreateVO vo = doCreate(request, userId, orderNo);
            if (claimed) {
                idempotentGuard.complete(idemKey, vo.paymentNo(), Duration.ofHours(idemTtlHours));
            }
            return vo;
        } catch (RuntimeException e) {
            if (claimed) {
                idempotentGuard.release(idemKey);
            }
            throw e;
        }
    }

    private PayCreateVO doCreate(PayCreateRequest request, Long userId, String orderNo) {
        PayChannel channel = channelRegistry.current();
        OrderPayView order = orderClient.getPayView(orderNo);
        requireOrderOwnership(order, userId);
        if (order.status() == null || order.status() != ORDER_STATUS_CREATED) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单状态不允许发起支付: " + order.statusLabel());
        }

        // 同一订单只允许一张未关闭支付单：新建前 CAS 关闭旧单（ADR-020）
        int closed = paymentMapper.closeOpenByOrderNo(orderNo, PaymentStatus.CLOSED.code(), LocalDateTime.now());
        if (closed > 0) {
            log.info("发起支付时关闭同订单未关闭支付单: orderNo={}, closed={}", orderNo, closed);
        }

        // paymentNo 必须先于渠道下单生成：Mock/真实通道都要用支付单号作预下单流水的前缀，
        // 否则会拿到 MOCK_PREPAY_null 这种"参数里嵌了 null"的脏数据（首次联调实测踩到）
        String paymentNo = PAYMENT_NO_PREFIX + SnowflakeIdGenerator.nextId();
        ChannelPayResult channelResult = channel.unifiedOrder(paymentNo, order.payAmount(), request.payType());
        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(orderNo);
        payment.setUserId(order.userId());
        payment.setRequestId(request.requestId());
        payment.setChannel(channel.code());
        payment.setPayType(request.payType());
        payment.setAmount(order.payAmount());
        payment.setStatus(PaymentStatus.PAYING.code());
        payment.setPrepayId(channelResult.prepayId());
        payment.setChannelTradeNo(channelResult.channelTradeNo());
        payment.setExpireAt(order.expireAt());
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            // 并发重投：唯一索引兜底，返回已存在的那张单
            Payment replay = paymentMapper.selectByOrderRequest(orderNo, request.requestId());
            if (replay == null) {
                throw e;
            }
            log.info("发起支付并发幂等命中: orderNo={}, paymentNo={}", orderNo, replay.getPaymentNo());
            return toCreateVO(replay, true);
        }
        log.info("支付单创建成功: paymentNo={}, orderNo={}, amount={}, channel={}",
                paymentNo, orderNo, payment.getAmount(), payment.getChannel());
        return toCreateVO(payment, false);
    }

    // ------------------------------------------------------------------ 查询支付单

    /** 支付单查询：仅本人或客服可查（28.4 / 28.11 #10）。 */
    public PaymentVO get(String paymentNo, Long userId) {
        Payment payment = requirePayment(paymentNo);
        requirePaymentOwnership(payment, userId);
        return toVO(payment);
    }

    // ------------------------------------------------------------------ 渠道回调

    /**
     * 渠道回调处理（28.6 七步）。
     *
     * <p>顺序固定：解析 → 时间窗 → 验签 → 防重放（落流水占唯一索引）→ 金额校验 → 状态 CAS → 通知订单。
     * 任何一步失败都要留痕；<b>失败留痕的 {@code channel_trade_no} 一律写 NULL</b>，
     * 以免"验签失败的脏请求"把唯一索引占掉，导致渠道改对签名后重试被误判成重复事件。
     */
    public CallbackResultVO handleCallback(String channelCode, CallbackRequest request, String remoteIp) {
        PayChannel channel = channelRegistry.byCode(channelCode);
        String secret = properties.getChannel().getSecret();

        // 1 解析报文：字段不全直接拒绝，不落流水（28.6 表：留痕为"—"）
        requireCallbackFields(request);

        long now = System.currentTimeMillis();
        long toleranceMillis = properties.getCallback().getToleranceSeconds() * 1000L;

        // 2 时间窗
        if (Math.abs(now - request.timestamp()) > toleranceMillis) {
            saveLog(channel, request, remoteIp, 0, CallbackProcessResult.SIGN_FAILED, false);
            throw new BusinessException(ResultCode.CALLBACK_EXPIRED,
                    "回调已过期: 时间偏差 " + (now - request.timestamp()) + "ms 超出 "
                            + properties.getCallback().getToleranceSeconds() + "s");
        }

        // 3 验签（常量时间比较）
        boolean signValid = PaySigner.verify(callbackSignParams(request), request.sign(), secret);
        if (!signValid) {
            saveLog(channel, request, remoteIp, 0, CallbackProcessResult.SIGN_FAILED, false);
            log.warn("支付回调验签失败: channel={}, paymentNo={}, remoteIp={}",
                    channel.code(), request.paymentNo(), remoteIp);
            throw new BusinessException(ResultCode.SIGN_INVALID, "回调验签失败");
        }

        // 4 防重放：先落流水（带 channel_trade_no，占住 uk_event），重复事件在此被拦下
        Long logId;
        try {
            logId = saveLog(channel, request, remoteIp, 1, CallbackProcessResult.SUCCESS, true);
        } catch (DuplicateKeyException e) {
            Payment current = paymentMapper.selectByPaymentNo(request.paymentNo());
            log.info("重复回调（uk_event 命中，幂等返回）: channel={}, channelTradeNo={}, paymentNo={}",
                    channel.code(), request.channelTradeNo(), request.paymentNo());
            return toCallbackVO(current, request, true);
        }

        // 5 金额校验
        Payment payment = paymentMapper.selectByPaymentNo(request.paymentNo());
        if (payment == null) {
            callbackLogMapper.updateProcessResult(logId, CallbackProcessResult.STATUS_ILLEGAL);
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND, "支付单不存在: " + request.paymentNo());
        }
        if (payment.getAmount().compareTo(request.amount()) != 0) {
            callbackLogMapper.updateProcessResult(logId, CallbackProcessResult.AMOUNT_MISMATCH);
            log.warn("回调金额不一致: paymentNo={}, local={}, callback={}",
                    request.paymentNo(), payment.getAmount(), request.amount());
            throw new BusinessException(ResultCode.PAY_AMOUNT_MISMATCH,
                    "回调金额与支付单金额不一致: " + request.amount() + " != " + payment.getAmount());
        }

        // 6 状态 CAS
        if (Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS.code())) {
            callbackLogMapper.updateProcessResult(logId, CallbackProcessResult.DUPLICATE_EVENT);
            log.info("支付单已是成功状态，回调幂等返回: paymentNo={}", payment.getPaymentNo());
            publishPaySuccessFallback(payment);
            return toCallbackVO(payment, request, true);
        }
        if (!Objects.equals(payment.getStatus(), PaymentStatus.PAYING.code())
                && !Objects.equals(payment.getStatus(), PaymentStatus.CREATED.code())) {
            callbackLogMapper.updateProcessResult(logId, CallbackProcessResult.STATUS_ILLEGAL);
            throw new BusinessException(ResultCode.PAYMENT_STATUS_ILLEGAL,
                    "支付单状态不允许该操作: " + PaymentStatus.of(payment.getStatus()).label());
        }

        if (isFailCallback(request.status())) {
            paymentMapper.markFailed(payment.getPaymentNo(), PaymentStatus.FAILED.code(), request.status());
            log.warn("支付失败回调: paymentNo={}, status={}", payment.getPaymentNo(), request.status());
            return toCallbackVO(paymentMapper.selectByPaymentNo(payment.getPaymentNo()), request, false);
        }

        int affected = paymentMapper.markPaid(payment.getPaymentNo(), PaymentStatus.SUCCESS.code(),
                request.channelTradeNo(), LocalDateTime.now());
        if (affected == 0) {
            Payment latest = paymentMapper.selectByPaymentNo(payment.getPaymentNo());
            if (latest != null && Objects.equals(latest.getStatus(), PaymentStatus.SUCCESS.code())) {
                callbackLogMapper.updateProcessResult(logId, CallbackProcessResult.DUPLICATE_EVENT);
                publishPaySuccessFallback(latest);
                return toCallbackVO(latest, request, true);
            }
            callbackLogMapper.updateProcessResult(logId, CallbackProcessResult.STATUS_ILLEGAL);
            throw new BusinessException(ResultCode.PAYMENT_STATUS_ILLEGAL, "支付单状态已变更，请重试");
        }

        Payment paid = paymentMapper.selectByPaymentNo(payment.getPaymentNo());
        channel.confirmPaid(paid.getPaymentNo(), request.channelTradeNo(), paid.getAmount(), LocalDate.now());

        // 7 通知订单：先同步（快路径），失败则落 Outbox 事件兜底（见 publishPaySuccessFallback）
        publishPaySuccessFallback(paid);
        log.info("支付回调处理成功: paymentNo={}, orderNo={}, channelTradeNo={}",
                paid.getPaymentNo(), paid.getOrderNo(), request.channelTradeNo());
        return toCallbackVO(paid, request, false);
    }

    /**
     * 通知订单「支付成功」：同步为快路径，失败/重复时落一条 {@code TOPIC_PAY_SUCCESS} 事件兜底。
     *
     * <p><b>这是本轮修复的核心</b>。原实现只有同步 Feign：通知失败仅记一行 ERROR，
     * 而 {@code PayQueryTask} 只扫 {@code status = 10} 的支付单、渠道重试命中
     * {@code uk_event} 时又直接返回、不再通知 —— 结果是「支付单 20 / 订单停在 10」
     * 且没有任何自动修复路径（28.9 声称有"查询任务与对账重放"，实际不存在）。
     *
     * <p>现在：同步成功 → 立即一致（零延迟，保持原有体验）；事件<b>始终</b>登记一次
     * （按 paymentNo 幂等，重复回调不会堆行），由补发任务投递，订单侧 handler 按状态幂等
     * （已是 20 直接返回）。因此即使同步通知丢失，订单也一定会被推进到 20。
     */
    private void publishPaySuccessFallback(Payment payment) {
        // 先试同步：成功则零延迟一致，也不影响后续事件（事件是幂等的保险）
        boolean notified = orderClient.notifyPaySuccess(payment.getOrderNo(), payment.getPaymentNo(),
                payment.getPayType(), payment.getAmount());

        // 同一 paymentNo 只登记一条：确定性 msgId 让重复回调/查询重放都落到同一行（19.6 幂等键口径）
        boolean inserted = outboxService.recordOnce(
                "PAY_SUCCESS",
                payment.getPaymentNo(),
                MessageTopics.PAY_SUCCESS,
                MessageTopics.TAG_PAID,
                payment.getOrderNo(),
                payment.getPaymentNo(),
                new MessageEvents.PaySuccessEvent(
                        payment.getOrderNo(),
                        payment.getPaymentNo(),
                        payment.getAmount(),
                        payment.getPayType(),
                        payment.getPaidAt() == null ? null : payment.getPaidAt().toString(),
                        payment.getChannelTradeNo()),
                0);

        if (!notified) {
            log.warn("订单同步回调失败，已由 Outbox 事件兜底（new={}），将重投直到订单侧成功: "
                            + "paymentNo={}, orderNo={}",
                    inserted, payment.getPaymentNo(), payment.getOrderNo());
        }
    }

    // ------------------------------------------------------------------ 主动查询兜底

    /**
     * 主动查询渠道并对齐（ADR-023）。
     *
     * <p>只允许 {@code 10 → 20/30}：已成功 / 已关闭的单不做任何"回退"，避免把终态改回去。
     */
    public CallbackResultVO sync(String paymentNo) {
        Payment payment = requirePayment(paymentNo);
        PayChannel channel = channelRegistry.byCode(payment.getChannel());

        if (Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS.code())
                || Objects.equals(payment.getStatus(), PaymentStatus.REFUNDED.code())) {
            // 已是终态：仍重放一次订单通知，修"支付成功但订单回调丢了"的场景（28.9）
            publishPaySuccessFallback(payment);
            saveQueryLog(channel, payment, CallbackProcessResult.SUCCESS);
            return toCallbackVO(payment, null, true);
        }
        if (Objects.equals(payment.getStatus(), PaymentStatus.CLOSED.code())) {
            throw new BusinessException(ResultCode.PAYMENT_STATUS_ILLEGAL, "支付单已关闭，不允许再对齐渠道状态");
        }

        ChannelQueryResult result = channel.query(payment.getPaymentNo(), payment.getChannelTradeNo(),
                payment.getAmount());
        if (result.status() == com.zhe.mall.pay.enums.ChannelTradeStatus.SUCCESS) {
            if (result.amount() != null && payment.getAmount().compareTo(result.amount()) != 0) {
                saveQueryLog(channel, payment, CallbackProcessResult.AMOUNT_MISMATCH);
                throw new BusinessException(ResultCode.PAY_AMOUNT_MISMATCH,
                        "渠道金额与支付单金额不一致: " + result.amount() + " != " + payment.getAmount());
            }
            int affected = paymentMapper.markPaid(payment.getPaymentNo(), PaymentStatus.SUCCESS.code(),
                    result.channelTradeNo(), LocalDateTime.now());
            Payment latest = paymentMapper.selectByPaymentNo(payment.getPaymentNo());
            if (affected > 0) {
                channel.confirmPaid(latest.getPaymentNo(), result.channelTradeNo(), latest.getAmount(), LocalDate.now());
                publishPaySuccessFallback(latest);
                log.info("主动查询对齐成功: paymentNo={}", payment.getPaymentNo());
            }
            saveQueryLog(channel, latest, CallbackProcessResult.SUCCESS);
            return toCallbackVO(latest, null, affected == 0);
        }
        if (result.status() == com.zhe.mall.pay.enums.ChannelTradeStatus.FAILED) {
            paymentMapper.markFailed(payment.getPaymentNo(), PaymentStatus.FAILED.code(),
                    result.failReason() == null ? "渠道返回支付失败" : result.failReason());
            saveQueryLog(channel, payment, CallbackProcessResult.SUCCESS);
            log.warn("主动查询对齐失败态: paymentNo={}, reason={}", payment.getPaymentNo(), result.failReason());
            return toCallbackVO(paymentMapper.selectByPaymentNo(payment.getPaymentNo()), null, false);
        }
        // PENDING / NOT_FOUND：渠道还没结果，保持原状，等下一轮
        saveQueryLog(channel, payment, CallbackProcessResult.SUCCESS);
        log.info("主动查询暂无结果: paymentNo={}, channelStatus={}", payment.getPaymentNo(), result.status());
        return toCallbackVO(payment, null, true);
    }

    // ------------------------------------------------------------------ 任务支撑

    /** 查询兜底候选（定时任务用）。 */
    public List<String> findQueryCandidates(int limit) {
        return paymentMapper.selectQueryCandidates(limit);
    }

    /** 关闭超时支付单：{@code 0/10 → 40} 的条件更新，单条原子（28.8）。 */
    public int closeExpired(int limit) {
        List<String> candidates = paymentMapper.selectExpiredCandidates(limit);
        int closed = 0;
        for (String paymentNo : candidates) {
            try {
                closed += paymentMapper.closeExpired(paymentNo, PaymentStatus.CLOSED.code(), LocalDateTime.now());
            } catch (Exception e) {
                log.warn("关闭超时支付单失败: paymentNo={}, reason={}", paymentNo, e.getMessage());
            }
        }
        return closed;
    }

    // ------------------------------------------------------------------ 内部工具

    private Payment requirePayment(String paymentNo) {
        if (paymentNo == null || paymentNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "paymentNo 不能为空");
        }
        Payment payment = paymentMapper.selectByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND, "支付单不存在: " + paymentNo);
        }
        return payment;
    }

    private void requireOrderOwnership(OrderPayView order, Long userId) {
        if (userId != null && userId.equals(order.userId())) {
            return;
        }
        CurrentUser currentUser = UserContext.get();
        if (currentUser != null && currentUser.isStaff()) {
            return;
        }
        log.warn("支付越权发起被拒绝: orderNo={}, ownerUserId={}, requestUserId={}",
                order.orderNo(), order.userId(), userId);
        throw new BusinessException(ResultCode.FORBIDDEN, "无该订单的支付权限");
    }

    private void requirePaymentOwnership(Payment payment, Long userId) {
        if (userId != null && userId.equals(payment.getUserId())) {
            return;
        }
        CurrentUser currentUser = UserContext.get();
        if (currentUser != null && currentUser.isStaff()) {
            return;
        }
        log.warn("支付单越权访问被拒绝: paymentNo={}, ownerUserId={}, requestUserId={}",
                payment.getPaymentNo(), payment.getUserId(), userId);
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该支付单");
    }

    private void requireCallbackFields(CallbackRequest request) {
        if (isBlank(request.paymentNo()) || isBlank(request.channelTradeNo()) || request.amount() == null
                || isBlank(request.status()) || request.timestamp() == null || isBlank(request.sign())) {
            throw new BusinessException(ResultCode.PARAM_MISSING,
                    "回调报文缺少必填字段: paymentNo / channelTradeNo / amount / status / timestamp / sign");
        }
    }

    /** 回调是否按"失败"处理：只认 SUCCESS，其余（含未知枚举）一律失败并告警（28.6 纪律 2）。 */
    private boolean isFailCallback(String status) {
        if (CALLBACK_STATUS_SUCCESS.equalsIgnoreCase(status)) {
            return false;
        }
        if (!CALLBACK_STATUS_FAIL.equalsIgnoreCase(status)) {
            log.warn("回调状态非法（按失败处理并告警）: status={}", status);
        }
        return true;
    }

    private Map<String, String> callbackSignParams(CallbackRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("paymentNo", request.paymentNo());
        params.put("channelTradeNo", request.channelTradeNo());
        params.put("amount", PaySigner.amountText(request.amount()));
        params.put("status", request.status());
        params.put("timestamp", request.timestamp() == null ? null : String.valueOf(request.timestamp()));
        return params;
    }

    /** 渠道支付参数：{@code nonceStr} 由 paymentNo 派生，保证幂等回放时参数完全一致。 */
    private ChannelParams channelParams(Payment payment) {
        String nonceStr = nonceFor(payment.getPaymentNo());
        Map<String, String> params = new HashMap<>();
        params.put("paymentNo", payment.getPaymentNo());
        params.put("prepayId", payment.getPrepayId());
        params.put("amount", PaySigner.amountText(payment.getAmount()));
        params.put("nonceStr", nonceStr);
        String sign = PaySigner.sign(params, properties.getChannel().getSecret());
        return new ChannelParams(payment.getPrepayId(), nonceStr, sign, "HMAC-SHA256");
    }

    private String nonceFor(String paymentNo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(paymentNo.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", raw[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return paymentNo;
        }
    }

    /**
     * 落回调流水。
     *
     * @param occupyEventKey 是否占用 {@code uk_event}：只有"首次受理的渠道事件"才占，
     *                       失败留痕与查询流水一律传 {@code false}（trade_no 写 NULL）
     */
    private Long saveLog(PayChannel channel, CallbackRequest request, String remoteIp,
                         int signValid, int processResult, boolean occupyEventKey) {
        PaymentCallbackLog row = new PaymentCallbackLog();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setPaymentNo(request.paymentNo());
        row.setChannel(channel.code());
        row.setEventType(CallbackEventType.PAY_CALLBACK.name());
        row.setChannelTradeNo(occupyEventKey ? request.channelTradeNo() : null);
        row.setAmount(request.amount());
        row.setSignValid(signValid);
        row.setProcessResult(processResult);
        row.setRawBody(truncate(rawBody(request)));
        row.setRemoteIp(remoteIp);
        callbackLogMapper.insert(row);
        return row.getId();
    }

    private void saveQueryLog(PayChannel channel, Payment payment, int processResult) {
        try {
            PaymentCallbackLog row = new PaymentCallbackLog();
            row.setId(SnowflakeIdGenerator.nextId());
            row.setPaymentNo(payment == null ? null : payment.getPaymentNo());
            row.setChannel(channel.code());
            row.setEventType(CallbackEventType.QUERY.name());
            // 查询流水不参与防重放，channel_trade_no 留空
            row.setChannelTradeNo(null);
            row.setAmount(payment == null ? null : payment.getAmount());
            row.setSignValid(1);
            row.setProcessResult(processResult);
            row.setRawBody("主动查询渠道状态");
            callbackLogMapper.insert(row);
        } catch (RuntimeException e) {
            log.warn("查询流水落库失败（不影响对齐结果）: paymentNo={}, error={}",
                    payment == null ? null : payment.getPaymentNo(), e.getMessage());
        }
    }

    private String rawBody(CallbackRequest request) {
        return "paymentNo=" + request.paymentNo() + ",channelTradeNo=" + request.channelTradeNo()
                + ",amount=" + request.amount() + ",status=" + request.status()
                + ",payType=" + request.payType() + ",timestamp=" + request.timestamp()
                + ",sign=" + maskSign(request.sign());
    }

    /** 签名只留前 6 位：留痕是为了排障，不是为了把可重放的凭据抄一份到审计表（21.4）。 */
    private String maskSign(String sign) {
        if (sign == null) {
            return null;
        }
        return sign.length() <= 6 ? "***" : sign.substring(0, 6) + "***";
    }

    private String truncate(String raw) {
        if (raw == null || raw.length() <= RAW_BODY_MAX) {
            return raw;
        }
        return raw.substring(0, RAW_BODY_MAX - 1) + RAW_BODY_ELLIPSIS;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PayCreateVO toCreateVO(Payment payment, boolean idempotentReplay) {
        return new PayCreateVO(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmount(),
                payment.getPayType(), payment.getChannel(), payment.getStatus(),
                PaymentStatus.of(payment.getStatus()).label(), payment.getExpireAt(),
                channelParams(payment), idempotentReplay);
    }

    private PaymentVO toVO(Payment payment) {
        return new PaymentVO(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmount(),
                payment.getPayType(), payment.getChannel(), payment.getStatus(),
                PaymentStatus.of(payment.getStatus()).label(), payment.getChannelTradeNo(),
                payment.getExpireAt(), payment.getPaidAt(), payment.getClosedAt(), payment.getFailReason(),
                payment.getCreatedAt(), channelParams(payment));
    }

    private CallbackResultVO toCallbackVO(Payment payment, CallbackRequest request, boolean idempotentReplay) {
        if (payment == null) {
            return new CallbackResultVO(request == null ? null : request.paymentNo(), null, null,
                    null, null, request == null ? null : request.channelTradeNo(), idempotentReplay);
        }
        return new CallbackResultVO(payment.getPaymentNo(), payment.getStatus(),
                PaymentStatus.of(payment.getStatus()).label(), payment.getOrderNo(), null,
                payment.getChannelTradeNo(), idempotentReplay);
    }
}
