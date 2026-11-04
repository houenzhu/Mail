package com.zhe.mall.api.dto;

/**
 * 库存域统计（看板售罄率口径）。
 *
 * <p>与 `product.sales` 无关：销量没有可靠写入源（计划文档 G-12），
 * 所以"售罄率"用库存真实字段计算，而不是用销量占比推算。
 *
 * @param skuTotal    库存记录总数
 * @param skuSoldOut  售罄 SKU 数（total>0 且 available=0）
 * @param skuZeroStock 零库存 SKU 数（total=0，通常是未备货的新品）
 * @param lowStock    available <= warn_threshold 的 SKU 数（含售罄）
 */
public record AdminInventoryStat(
        long skuTotal,
        long skuSoldOut,
        long skuZeroStock,
        long lowStock) {
}
