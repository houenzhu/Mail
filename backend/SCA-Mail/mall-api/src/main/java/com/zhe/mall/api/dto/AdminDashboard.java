package com.zhe.mall.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 后台数据看板（UC-ADMIN-03）。
 *
 * <p>由 BFF 跨服务聚合：订单与 GMV 来自 `mall-order`，退款待处理数来自 `mall-pay`，
 * 售罄率来自 `mall-inventory`（database-per-service，禁止跨库 JOIN）。
 *
 * @param overview     概览指标
 * @param trend        近 N 日趋势（日期升序，缺失日期补 0）
 * @param days         趋势口径天数
 * @param skuTotal     SKU 库存记录总数
 * @param skuSoldOut   售罄 SKU 数（total>0 且 available=0）
 * @param skuZeroStock 零库存 SKU 数（total=0）
 * @param sellOutRate  售罄率（skuSoldOut / skuTotal，4 位小数，null 表示无库存记录）
 * @param lowStock     available <= warn_threshold 的 SKU 数
 */
public record AdminDashboard(
        AdminDashboardOverview overview,
        List<AdminDashboardTrendPoint> trend,
        int days,
        long skuTotal,
        long skuSoldOut,
        long skuZeroStock,
        BigDecimal sellOutRate,
        long lowStock) {
}
