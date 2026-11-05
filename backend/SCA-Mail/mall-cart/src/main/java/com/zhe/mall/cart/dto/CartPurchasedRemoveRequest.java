package com.zhe.mall.cart.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 下单成功后清理已购入的 SKU（只影响当前登录用户自己的购物车）。 */
public record CartPurchasedRemoveRequest(
        @NotEmpty(message = "skuIds 不能为空") List<Long> skuIds) {
}
