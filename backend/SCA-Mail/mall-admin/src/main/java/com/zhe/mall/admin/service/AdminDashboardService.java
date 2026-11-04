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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 后台数据看板聚合（UC-ADMIN-03 / 计划文档 G-10、M5）。
 *
 * <p>三个数据域各自独立库（database-per-service），BFF 只做聚合，不跨库 JOIN：
 * <ul>
 *   <li>订单与 GMV：{@code mall-order}</li>
 *   <li>待处理退款单：{@code mall-pay}</li>
 *   <li>售罄率与低库存：{@code mall-inventory}</li>
 * </ul>
 *
 * <p><b>降级口径</b>：订单与 GMV 是主指标，失败必须报错；
 * 退款待处理数与库存统计属于辅助指标，下游不可用时置 0 并打 WARN，
 * 避免一次依赖抖动把整个看板打成错误页。
 */
@Service
public class AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);

    private static final int MAX_DAYS = 90;

    private final OrderApi orderApi;

    private final PayApi payApi;

    private final InventoryApi inventoryApi;

    public AdminDashboardService(OrderApi orderApi, PayApi payApi, InventoryApi inventoryApi) {
        this.orderApi = orderApi;
        this.payApi = payApi;
        this.inventoryApi = inventoryApi;
    }

    public AdminDashboard load(Integer days) {
        int safeDays = days == null ? 7 : Math.max(1, Math.min(MAX_DAYS, days));

        AdminDashboardOverview orderOverview = unwrap(orderApi.adminDashboardOverview(), "查询订单概览");
        List<AdminDashboardTrendPoint> trend = unwrap(orderApi.adminDashboardTrend(safeDays), "查询订单趋势");

        long refundPending = safeRefundPending();
        AdminInventoryStat inventory = safeInventoryStat();

        return new AdminDashboard(
                new AdminDashboardOverview(
                        orderOverview.gmvToday(),
                        orderOverview.ordersToday(),
                        orderOverview.toShip(),
                        orderOverview.refunding(),
                        refundPending),
                trend,
                safeDays,
                inventory.skuTotal(),
                inventory.skuSoldOut(),
                inventory.skuZeroStock(),
                sellOutRate(inventory.skuSoldOut(), inventory.skuTotal()),
                inventory.lowStock());
    }

    /**
     * 售罄率 = 售罄 SKU / 有库存记录的 SKU，保留 4 位小数。
     *
     * <p>分母为 0 时返回 {@code null} 而不是 0：没有库存记录不等于"售罄率 0%"，
     * 前端应显示 "—" 而不是误导性的 0。
     */
    static BigDecimal sellOutRate(long soldOut, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(soldOut)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private long safeRefundPending() {
        try {
            Long value = unwrap(payApi.adminRefundPendingCount(), "查询待处理退款数");
            return value == null ? 0L : value;
        } catch (RuntimeException e) {
            log.warn("待处理退款数不可用，看板按 0 展示: {}", e.getMessage());
            return 0L;
        }
    }

    private AdminInventoryStat safeInventoryStat() {
        try {
            AdminInventoryStat stat = unwrap(inventoryApi.adminStat(), "查询库存统计");
            return stat == null ? new AdminInventoryStat(0, 0, 0, 0) : stat;
        } catch (RuntimeException e) {
            log.warn("库存统计不可用，看板按 0 展示: {}", e.getMessage());
            return new AdminInventoryStat(0, 0, 0, 0);
        }
    }

    private <T> T unwrap(Result<T> result, String action) {
        if (result == null || result.code() != ResultCode.SUCCESS.getCode()) {
            String message = result == null ? "下游无响应" : result.message();
            log.warn("{}失败: code={}, message={}", action,
                    result == null ? null : result.code(), message);
            throw new BusinessException(
                    result == null ? ResultCode.DOWNSTREAM_UNAVAILABLE.getCode() : result.code(),
                    action + "失败：" + message);
        }
        return result.data();
    }
}
