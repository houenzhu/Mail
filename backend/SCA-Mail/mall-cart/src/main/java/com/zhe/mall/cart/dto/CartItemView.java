package com.zhe.mall.cart.dto;

import java.math.BigDecimal;

/**
 * 购物车行视图：商品 / SKU 快照按实时数据刷新，库存也实时查询。
 *
 * <p>{@code invalid} 为 true 时前端应展示失效原因并禁止勾选结算；
 * 失效商品不会自动删除，由用户显式删除。
 */
public record CartItemView(Long skuId, Long productId, String productName, String skuName,
                           String specJson, String image, BigDecimal price, BigDecimal marketPrice,
                           Integer quantity, Boolean selected, Integer available,
                           Boolean invalid, String invalidReason) {
}
