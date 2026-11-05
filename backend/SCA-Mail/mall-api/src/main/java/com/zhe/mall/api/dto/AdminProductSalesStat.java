package com.zhe.mall.api.dto;

/**
 * 商品近 N 日销量聚合结果（mall-order → mall-admin）。
 *
 * @param productId 商品 ID
 * @param quantity  该时间窗内的成交件数（sum(order_item.qty)）
 */
public record AdminProductSalesStat(Long productId, Integer quantity) {
}
