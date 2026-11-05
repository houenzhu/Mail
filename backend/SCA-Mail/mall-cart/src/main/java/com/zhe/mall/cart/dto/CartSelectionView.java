package com.zhe.mall.cart.dto;

/** 勾选变更结果：受影响的 SKU 数量与最新购物车。 */
public record CartSelectionView(int changed, CartView cart) {
}
