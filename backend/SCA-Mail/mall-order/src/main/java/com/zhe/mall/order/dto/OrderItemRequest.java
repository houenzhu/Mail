package com.zhe.mall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull(message = "skuId 不能为空") Long skuId,
        @NotNull(message = "购买数量不能为空") @Min(value = 1, message = "购买数量必须大于 0") Integer qty) {
}
