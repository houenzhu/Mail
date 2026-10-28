package com.zhe.mall.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 保存商品（SPU + SKU）入参，**新增与编辑共用一份**。
 *
 * <p>放在 mall-api 而不是某个服务内部：Feign 的请求体类型必须两端共享同一份定义，
 * 否则 BFF 与下游各持一份 record，字段一旦漂移就会出现"前端提交成功、下游字段为 null"这类静默故障
 * （与 {@link AdminProductPageQuery}、{@link DeductRequest} 同一约定）。
 *
 * <p>新增与编辑共用：编辑时 {@link SkuSaveRequest#skuId()} 非空表示"更新已有 SKU"，
 * 为空表示"新增 SKU"；库中存在但请求里没带的 SKU 会被软删除（差量更新）。
 * 这样避免 create 一套 / update 一套两份近乎重复的 DTO。
 */
public record ProductSaveRequest(
        @NotBlank(message = "商品名称不能为空") @Size(max = 128, message = "商品名称过长") String name,
        String subtitle,
        @NotNull(message = "分类不能为空") Long categoryId,
        Long brandId,
        String mainImage,
        String detail,
        Integer status,
        @NotEmpty(message = "至少需要一个SKU") @Valid List<SkuSaveRequest> skus
) {
}