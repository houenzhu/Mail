package com.zhe.mall.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 加入购物车：同一 SKU 累加数量。 */
public record CartAddRequest(
        @NotNull(message = "skuId 不能为空") Long skuId,
        @NotNull(message = "购买数量不能为空") @Min(value = 1, message = "购买数量必须大于 0") Integer quantity) {
}
