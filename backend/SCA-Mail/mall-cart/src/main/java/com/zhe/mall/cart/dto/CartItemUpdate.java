package com.zhe.mall.cart.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 购物车单品修改。
 *
 * <p>{@code quantity} 为 0 或负数时删除该 SKU；{@code selected} 为 null 表示不改勾选状态。
 */
public record CartItemUpdate(
        @NotNull(message = "skuId 不能为空") Long skuId,
        Integer quantity,
        Boolean selected) {
}
