package com.zhe.mall.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车聚合视图。
 *
 * <p>{@code totalQuantity} 统计全部商品件数；{@code selectedCount} / {@code selectedAmount}
 * 只统计"已勾选且有效"的行，结算金额直接以此为准。
 */
public record CartView(List<CartItemView> items, int totalQuantity, int selectedCount,
                       BigDecimal selectedAmount, boolean allSelected) {
}
