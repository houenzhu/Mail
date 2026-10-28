package com.zhe.mall.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 保存 SKU 入参（见 {@link ProductSaveRequest}）。
 *
 * <p>{@code skuId} 为空表示新增 SKU；非空表示更新已有 SKU（主键不变，因此其库存自然保留）。
 *
 * <p>{@code stock} 只在**新增 SKU** 时生效：编辑页不改已有 SKU 的库存，
 * 库存变更统一走库存管理页并写流水，可审计、可对账，也避免商品事务里跨服务写库存。
 */
public record SkuSaveRequest(
        Long skuId,
        @NotBlank(message = "SKU编码不能为空") String skuCode,
        @NotBlank(message = "SKU名称不能为空") String name,
        String specJson,
        @NotNull(message = "售价不能为空") @DecimalMin(value = "0.01", message = "售价必须大于0") BigDecimal price,
        BigDecimal marketPrice,
        String image,
        @Min(value = 0, message = "初始库存不能为负") Integer stock
) {
}