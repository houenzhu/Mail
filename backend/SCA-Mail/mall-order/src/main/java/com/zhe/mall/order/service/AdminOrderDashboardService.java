package com.zhe.mall.order.service;

import com.zhe.mall.api.dto.AdminDashboardOverview;
import com.zhe.mall.api.dto.AdminDashboardTrendPoint;
import com.zhe.mall.api.dto.AdminOrderCountStat;
import com.zhe.mall.api.dto.AdminOrderDailyCount;
import com.zhe.mall.api.dto.AdminOrderDailyStat;
import com.zhe.mall.order.enums.OrderStatus;
import com.zhe.mall.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台看板的订单侧统计（UC-ADMIN-03）。
 *
 * <p>与 {@link AdminOrderQueryService} 分开：本类只做聚合，不返回订单行，
 * 便于单独测试"日期补零 / 边界归属 / GMV 口径"这些容易算错的地方。
 *
 * <p><b>时区</b>：需求要求"以服务端（Asia/Shanghai）为准"，因此日界线用固定时区计算，
 * 而不是跟随 JVM 默认时区——同一份代码在 UTC 机器上跑不应把 08:00 前的订单算到前一天。
 */
@Service
public class AdminOrderDashboardService {

    /** 业务时区固定为 Asia/Shanghai（需求 16.5 / 验收标准"时间以服务端为准"）。 */
    static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int MAX_DAYS = 90;

    private final OrderMapper orderMapper;

    public AdminOrderDashboardService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public AdminDashboardOverview overview() {
        LocalDate today = LocalDate.now(BIZ_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();

        // 有成交的状态：已支付/已发货/已完成/退款中/已退款
        // 待支付(10)、已取消(50) 不产生收入，不计入 GMV。
        // 只查今天这一段：多查 7 天再过滤，扫出来的行不会被用到。
        List<AdminOrderDailyStat> daily = orderMapper.selectDailyStats(todayStart, tomorrow);
        BigDecimal gmvToday = daily.stream()
                .map(AdminOrderDailyStat::gmv)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long ordersToday = orderMapper.countCreatedBetween(todayStart, tomorrow);

        Map<Integer, Long> byStatus = statusCounts();
        return new AdminDashboardOverview(
                gmvToday,
                ordersToday,
                byStatus.getOrDefault(OrderStatus.PAID.code(), 0L),
                byStatus.getOrDefault(OrderStatus.REFUNDING.code(), 0L),
                0L);
    }

    /**
     * 近 N 日趋势，日期升序且**补齐无数据日期**。
     *
     * <p>不补零的话，前端折线会把 9-15 与 11-04 两个点相邻绘制，
     * 看起来像"持续增长"，实际中间几十天没有任何成交。
     */
    public List<AdminDashboardTrendPoint> trend(int days) {
        int safeDays = Math.max(1, Math.min(MAX_DAYS, days));
        LocalDate today = LocalDate.now(BIZ_ZONE);
        LocalDate startDate = today.minusDays(safeDays - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        Map<LocalDate, BigDecimal> gmvByDate = new HashMap<>();
        Map<LocalDate, Long> paidByDate = new HashMap<>();
        for (AdminOrderDailyStat stat : orderMapper.selectDailyStats(start, end)) {
            if (stat.date() == null) {
                continue;
            }
            gmvByDate.merge(stat.date(), stat.gmv() == null ? BigDecimal.ZERO : stat.gmv(), BigDecimal::add);
            paidByDate.merge(stat.date(), stat.paidOrders(), Long::sum);
        }

        Map<LocalDate, Long> ordersByDate = new HashMap<>();
        for (AdminOrderDailyCount stat : orderMapper.selectDailyOrderCounts(start, end)) {
            if (stat.date() != null) {
                ordersByDate.merge(stat.date(), stat.orders(), Long::sum);
            }
        }

        List<AdminDashboardTrendPoint> points = new ArrayList<>(safeDays);
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = startDate.plusDays(i);
            points.add(new AdminDashboardTrendPoint(
                    date,
                    gmvByDate.getOrDefault(date, BigDecimal.ZERO),
                    ordersByDate.getOrDefault(date, 0L),
                    paidByDate.getOrDefault(date, 0L)));
        }
        return points;
    }

    private Map<Integer, Long> statusCounts() {
        Map<Integer, Long> byStatus = new HashMap<>();
        for (AdminOrderCountStat stat : orderMapper.selectStatusCounts()) {
            if (stat.status() != null) {
                byStatus.merge(stat.status(), stat.total(), Long::sum);
            }
        }
        return byStatus;
    }
}
