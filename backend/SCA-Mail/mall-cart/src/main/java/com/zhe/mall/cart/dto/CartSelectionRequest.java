package com.zhe.mall.cart.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 批量勾选 / 取消勾选；skuIds 为空表示全选或全不选。 */
public record CartSelectionRequest(
        List<Long> skuIds,
        @NotNull(message = "selected 不能为空") Boolean selected) {
}
