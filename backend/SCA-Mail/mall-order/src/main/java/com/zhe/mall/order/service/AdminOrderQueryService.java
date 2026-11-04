package com.zhe.mall.order.service;

import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderItemDTO;
import com.zhe.mall.api.dto.AdminOrderLogDTO;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.order.dto.OrderItemStat;
import com.zhe.mall.order.entity.Order;
import com.zhe.mall.order.entity.OrderItem;
import com.zhe.mall.order.entity.OrderStatusLog;
import com.zhe.mall.order.enums.OrderStatus;
import com.zhe.mall.order.mapper.OrderItemMapper;
import com.zhe.mall.order.mapper.OrderMapper;
import com.zhe.mall.order.mapper.OrderStatusLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台订单查询（运营视角）。
 *
 * <p>与 C 端 {@link OrderService} 的查询方法刻意分开：
 * <ul>
 *   <li>C 端强制按当前登录用户过滤，后台按条件全量查；</li>
 *   <li>后台需要展示收货信息、明细件数等运营字段，C 端不需要；</li>
 *   <li>写操作（发货）在 {@link OrderService} 里：状态机、CAS 与状态日志都在那一处，避免两套实现。</li>
 * </ul>
 *
 * <p><b>角色校验不在这里</b>：身份由网关注入，本服务控制器上标注 {@code @RequireStaff}，
 * 服务层只负责数据组装，避免同一规则两处实现。
 */
@Service
public class AdminOrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderQueryService.class);

    /** 只接受严格格式，避免 2026/11/01 这类输入被误判为合法日期。 */
    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final Pattern DATE_TIME =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final OrderStatusLogMapper orderStatusLogMapper;

    public AdminOrderQueryService(OrderMapper orderMapper,
                                  OrderItemMapper orderItemMapper,
                                  OrderStatusLogMapper orderStatusLogMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
    }

    public PageResult<AdminOrderRow> page(AdminOrderPageQuery query) {
        AdminOrderPageQuery effective = normalize(query);
        long total = orderMapper.countAdminPage(effective);
        if (total == 0) {
            return PageResult.empty(effective.safePageNo(), effective.safePageSize());
        }
        List<Order> orders = orderMapper.selectAdminPage(
                effective, effective.offset(), effective.safePageSize());
        Map<String, OrderItemStat> stats = itemStats(orders);
        List<AdminOrderRow> rows = orders.stream()
                .map(order -> toRow(order, stats.get(order.getOrderNo())))
                .toList();
        return PageResult.of(total, effective.safePageNo(), effective.safePageSize(), rows);
    }

    public AdminOrderDetail detail(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "orderNo 不能为空");
        }
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        List<OrderItem> items = orderItemMapper.selectByOrderNo(orderNo);
        List<OrderStatusLog> logs = orderStatusLogMapper.selectByOrderNo(orderNo);

        List<AdminOrderItemDTO> itemDTOs = items.stream()
                .map(item -> new AdminOrderItemDTO(item.getSkuId(), item.getProductId(),
                        item.getProductName(), item.getSkuSpec(), item.getPrice(),
                        item.getQty(), item.getTotalAmount()))
                .toList();
        List<AdminOrderLogDTO> logDTOs = logs.stream()
                .map(this::toLog)
                .toList();
        OrderItemStat stat = new OrderItemStat(orderNo, itemDTOs.size(),
                itemDTOs.stream().mapToInt(AdminOrderItemDTO::qty).sum());
        return new AdminOrderDetail(toRow(order, stat), itemDTOs, logDTOs);
    }

    /**
     * 把前端传来的时间文本规整成可比较的 LocalDateTime。
     *
     * <p>只接受两种格式：{@code yyyy-MM-dd}（日期区间，结束日自动补到 23:59:59.999）
     * 与 {@code yyyy-MM-dd HH:mm:ss}。格式不对直接报参数错误，而不是静默忽略条件——
     * 静默忽略会让运营以为筛选生效了。
     */
    private AdminOrderPageQuery normalize(AdminOrderPageQuery query) {
        if (query == null) {
            return new AdminOrderPageQuery(null, null, null, null, null, 1, 10);
        }
        String orderNo = blankToNull(query.orderNo());
        Integer status = query.status();
        if (status != null) {
            // 提前校验状态值，避免把 999 这种值直接丢给 SQL（查不到数据但也不报错）
            OrderStatus.of(status);
        }
        return new AdminOrderPageQuery(orderNo, query.userId(), status,
                normalizeStart(query.createdAtStart()),
                normalizeEnd(query.createdAtEnd()),
                query.pageNo(), query.pageSize());
    }

    private String normalizeStart(String value) {
        LocalDateTime parsed = parseTime(value, false);
        return parsed == null ? null : parsed.toString();
    }

    private String normalizeEnd(String value) {
        LocalDateTime parsed = parseTime(value, true);
        return parsed == null ? null : parsed.toString();
    }

    /**
     * 解析筛选时间。
     *
     * <p>按**内容**判断格式而不是按长度：{@code 2026/11/01} 同样是 10 个字符，
     * 但它是非法输入，必须报错而不是被当成合法日期。只接受
     * {@code yyyy-MM-dd}（{@code endOfDay=true} 时补到 23:59:59.999）
     * 与 {@code yyyy-MM-dd HH:mm:ss} 两种格式。
     */
    private LocalDateTime parseTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (DATE_ONLY.matcher(trimmed).matches()) {
                LocalDate date = LocalDate.parse(trimmed);
                return endOfDay
                        ? date.atTime(LocalTime.of(23, 59, 59, 999_000_000))
                        : date.atStartOfDay();
            }
            if (DATE_TIME.matcher(trimmed).matches()) {
                return LocalDateTime.parse(trimmed.replace(' ', 'T'));
            }
        } catch (DateTimeParseException e) {
            throw invalidTime(trimmed);
        }
        throw invalidTime(trimmed);
    }

    private BusinessException invalidTime(String value) {
        return new BusinessException(ResultCode.PARAM_INVALID,
                "时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss: " + value);
    }

    private Map<String, OrderItemStat> itemStats(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<String> orderNos = orders.stream().map(Order::getOrderNo).toList();
        return orderItemMapper.selectStatsByOrderNos(orderNos).stream()
                .collect(Collectors.toMap(OrderItemStat::orderNo, stat -> stat, (a, b) -> a));
    }

    private AdminOrderRow toRow(Order order, OrderItemStat stat) {
        OrderStatus status = OrderStatus.of(order.getStatus());
        return new AdminOrderRow(order.getOrderNo(), order.getUserId(), order.getStatus(),
                status.label(), order.getTotalAmount(), order.getDiscountAmount(),
                order.getPayAmount(), order.getPayType(), order.getReceiverName(),
                order.getReceiverPhone(), order.getReceiverAddress(), order.getRemark(),
                order.getExpireAt(), order.getPaidAt(), order.getCancelledAt(),
                order.getCancelReason(), order.getShippingCompany(), order.getTrackingNo(),
                order.getShippedAt(), order.getShippedBy(), order.getCreatedAt(),
                stat == null ? 0 : stat.itemCount(),
                stat == null ? 0 : stat.totalQty());
    }

    private AdminOrderLogDTO toLog(OrderStatusLog log) {
        return new AdminOrderLogDTO(log.getFromStatus(), labelOf(log.getFromStatus()),
                log.getToStatus(), labelOf(log.getToStatus()),
                log.getOperator(), log.getRemark(), log.getCreatedAt());
    }

    /**
     * 历史数据里可能出现当前枚举不认识的旧状态码；时间线不能因此 500，
     * 用原始码兜底展示，同时留一条 WARN 便于发现脏数据。
     */
    private String labelOf(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return OrderStatus.of(code).label();
        } catch (RuntimeException e) {
            log.warn("订单状态日志出现未知状态码: {}", code);
            return String.valueOf(code);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

