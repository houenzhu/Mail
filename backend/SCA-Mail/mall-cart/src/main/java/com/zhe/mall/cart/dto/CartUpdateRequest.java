package com.zhe.mall.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 批量修改数量 / 勾选（UC-CART-02）。 */
public record CartUpdateRequest(
        @NotEmpty(message = "修改项不能为空") @Valid List<CartItemUpdate> items) {
}
