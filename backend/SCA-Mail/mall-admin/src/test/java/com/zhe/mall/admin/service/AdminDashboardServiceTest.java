package com.zhe.mall.admin.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.OrderApi;
import com.zhe.mall.api.PayApi;
import com.zhe.mall.api.dto.AdminDashboard;
import com.zhe.mall.api.dto.AdminDashboardOverview;
import com.zhe.mall.api.dto.AdminDashboardTrendPoint;
import com.zhe.mall.api.dto.AdminInventoryStat;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 看板 BFF 聚合的单测。
 *
 * <p>覆盖三件事：
 * <ul>
 *   <li>售罄率算法与"分母为 0 返回 null"（不能把"没有库存记录"显示成 0%）；</li>
 *   <li>订单侧失败必须报错（主指标不能伪装成空数据）；</li>
 *   <li>退款/库存失败降级为 0 并保留订单指标（辅助指标允许降级）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private OrderApi orderApi;

    @Mock
    private PayApi payApi;

    @Mock
    private InventoryApi inventoryApi;

    @InjectMocks
    private AdminDashboardService service;

    private static <T> Result<T> ok(T data) {
        return Result.success(data);
    }

    @Test
    @DisplayName("售罄率：保留 4 位小数；分母为 0 返回 null 而不是 0")
    void sellOutRateHandlesZeroDenominator() {
        assertThat(AdminDashboardService.sellOutRate(1, 3)).isEqualByComparingTo("0.3333");
        assertThat(AdminDashboardService.sellOutRate(0, 8)).isEqualByComparingTo("0.0000");
        assertThat(AdminDashboardService.sellOutRate(0, 0)).isNull();
    }

    @Test
    @DisplayName("聚合：合并订单概览、趋势、退款与库存统计")
    void loadMergesAllDomains() {
        when(orderApi.adminDashboardOverview()).thenReturn(ok(
                new AdminDashboardOverview(new BigDecimal("99.00"), 3, 2, 1, 0)));
        when(orderApi.adminDashboardTrend(anyInt())).thenReturn(ok(List.of(
                new AdminDashboardTrendPoint(LocalDate.now(), new BigDecimal("99.00"), 3, 1))));
        when(payApi.adminRefundPendingCount()).thenReturn(ok(4L));
        when(inventoryApi.adminStat()).thenReturn(ok(new AdminInventoryStat(8, 2, 2, 3)));

        AdminDashboard dashboard = service.load(7);

        assertThat(dashboard.days()).isEqualTo(7);
        assertThat(dashboard.overview().gmvToday()).isEqualByComparingTo("99.00");
        assertThat(dashboard.overview().refundPending()).isEqualTo(4);
        assertThat(dashboard.skuTotal()).isEqualTo(8);
        assertThat(dashboard.skuSoldOut()).isEqualTo(2);
        assertThat(dashboard.skuZeroStock()).isEqualTo(2);
        assertThat(dashboard.sellOutRate()).isEqualByComparingTo("0.25");
        assertThat(dashboard.lowStock()).isEqualTo(3);
        assertThat(dashboard.trend()).hasSize(1);
    }

    @Test
    @DisplayName("聚合：天数非法时收敛到 1~90")
    void loadClampsDays() {
        when(orderApi.adminDashboardOverview()).thenReturn(ok(
                new AdminDashboardOverview(BigDecimal.ZERO, 0, 0, 0, 0)));
        when(orderApi.adminDashboardTrend(anyInt())).thenReturn(ok(List.of()));
        when(payApi.adminRefundPendingCount()).thenReturn(ok(0L));
        when(inventoryApi.adminStat()).thenReturn(ok(new AdminInventoryStat(0, 0, 0, 0)));

        assertThat(service.load(null).days()).isEqualTo(7);
        assertThat(service.load(0).days()).isEqualTo(1);
        assertThat(service.load(500).days()).isEqualTo(90);
    }

    @Test
    @DisplayName("订单侧失败必须上抛，不能伪装成空看板")
    void loadFailsWhenOrderDomainFails() {
        when(orderApi.adminDashboardOverview())
                .thenReturn(Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务不可用"));

        assertThatThrownBy(() -> service.load(7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询订单概览失败");
    }

    @Test
    @DisplayName("退款/库存失败降级为 0，订单指标仍然可用")
    void loadDegradesAuxiliaryDomains() {
        when(orderApi.adminDashboardOverview()).thenReturn(ok(
                new AdminDashboardOverview(new BigDecimal("50.00"), 1, 0, 0, 0)));
        when(orderApi.adminDashboardTrend(anyInt())).thenReturn(ok(List.of()));
        when(payApi.adminRefundPendingCount()).thenThrow(new RuntimeException("pay down"));
        when(inventoryApi.adminStat()).thenThrow(new RuntimeException("inventory down"));

        AdminDashboard dashboard = service.load(7);

        assertThat(dashboard.overview().gmvToday()).isEqualByComparingTo("50.00");
        assertThat(dashboard.overview().refundPending()).isZero();
        assertThat(dashboard.skuTotal()).isZero();
        assertThat(dashboard.sellOutRate()).isNull();
    }
}
