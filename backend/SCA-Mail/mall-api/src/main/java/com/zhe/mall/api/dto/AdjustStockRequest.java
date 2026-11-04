package com.zhe.mall.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 管理端库存增量调整请求。delta 为正数调增，为负数调减。 */
public record AdjustStockRequest(
        @NotBlank @Size(max = 32) String requestId,
        @NotNull @Positive Long skuId,
        @NotNull Integer delta,
        @Size(max = 255) String remark) {
}
