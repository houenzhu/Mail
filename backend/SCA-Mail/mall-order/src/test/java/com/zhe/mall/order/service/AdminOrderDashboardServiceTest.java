package com.zhe.mall.order.service;

import com.zhe.mall.api.dto.AdminDashboardOverview;
import com.zhe.mall.api.dto.AdminDashboardTrendPoint;
import com.zhe.mall.api.dto.AdminOrderCountStat;
import com.zhe.mall.api.dto.AdminOrderDailyCount;
import com.zhe.mall.api.dto.AdminOrderDailyStat;
import com.zhe.mall.order.mapper.OrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看板订单侧统计的单测。
 *
 * <p>重点覆盖最容易算错、又最难在人工验收中发现的点：
 * 趋势必须**逐日补零**（否则折线会跨天连成斜线）、
 * 天数参数必须收敛、概览只查今天而不是顺手扫 7 天。
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderDashboardServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private AdminOrderDashboardService service;

    @Test
    @DisplayName("趋势：逐日补零且日期升序，缺失日期 GMV 为 0")
    void trendFillsMissingDays() {
        LocalDate today = LocalDate.now(AdminOrderDashboardService.BIZ_ZONE);
        LocalDate start = today.minusDays(2);

        when(orderMapper.selectDailyStats(any(), any()))
                .thenReturn(List.of(new AdminOrderDailyStat(start, new BigDecimal("100.50"), 1)));
        when(orderMapper.selectDailyOrderCounts(any(), any()))
                .thenReturn(List.of(new AdminOrderDailyCount(today, 3)));

        List<AdminDashboardTrendPoint> trend = service.trend(3);

        assertThat(trend).hasSize(3);
        assertThat(trend.get(0).date()).isEqualTo(start);
        assertThat(trend.get(0).gmv()).isEqualByComparingTo("100.50");
        assertThat(trend.get(0).paidOrders()).isEqualTo(1);
        // 中间这一天没有数据 → 补 0，而不是被跳过
        assertThat(trend.get(1).gmv()).isEqualByComparingTo("0");
        assertThat(trend.get(1).orders()).isZero();
        assertThat(trend.get(2).date()).isEqualTo(today);
        assertThat(trend.get(2).orders()).isEqualTo(3);
    }

    @Test
    @DisplayName("趋势：天数收敛到 1~90，非法值不会打穿数据库")
    void trendClampsDays() {
        when(orderMapper.selectDailyStats(any(), any())).thenReturn(List.of());
        when(orderMapper.selectDailyOrderCounts(any(), any())).thenReturn(List.of());

        assertThat(service.trend(0)).hasSize(1);
        assertThat(service.trend(-5)).hasSize(1);
        assertThat(service.trend(1000)).hasSize(90);
    }

    @Test
    @DisplayName("概览：今日 GMV 由当天的按日统计汇总，待发货/退款中取状态计数")
    void overviewAggregatesToday() {
        LocalDate today = LocalDate.now(AdminOrderDashboardService.BIZ_ZONE);
        when(orderMapper.selectDailyStats(any(), any()))
                .thenReturn(List.of(new AdminOrderDailyStat(today, new BigDecimal("299.00"), 2)));
        when(orderMapper.countCreatedBetween(any(), any())).thenReturn(5L);
        when(orderMapper.selectStatusCounts()).thenReturn(List.of(
                new AdminOrderCountStat(10, 3),
                new AdminOrderCountStat(20, 4),
                new AdminOrderCountStat(60, 2)));

        AdminDashboardOverview overview = service.overview();

        assertThat(overview.gmvToday()).isEqualByComparingTo("299.00");
        assertThat(overview.ordersToday()).isEqualTo(5);
        assertThat(overview.toShip()).isEqualTo(4);
        assertThat(overview.refunding()).isEqualTo(2);
        assertThat(overview.refundPending()).isZero();
    }

    @Test
    @DisplayName("概览：只查今天一天，不顺手扫整周")
    void overviewQueriesOnlyToday() {
        when(orderMapper.selectDailyStats(any(), any())).thenReturn(List.of());
        when(orderMapper.countCreatedBetween(any(), any())).thenReturn(0L);
        when(orderMapper.selectStatusCounts()).thenReturn(List.of());

        service.overview();

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).selectDailyStats(start.capture(), end.capture());
        assertThat(end.getValue().toLocalDate()).isEqualTo(start.getValue().toLocalDate().plusDays(1));
        // 概览不需要调用趋势用的"按日下单数"
        verify(orderMapper, never()).selectDailyOrderCounts(any(), any());
    }
}
