package com.zhe.mall.api.dto;

import java.math.BigDecimal;

/**
 * SKU 视图。
 *
 * <p>{@code marketPrice}/{@code image} 是 M3 编辑商品回显所必需的：
 * 商品表单里"划线价/规格图"如果拿不回来，保存时会被默认值覆盖，等于静默改价。
 */
public record SkuDTO(Long id, Long productId, String skuCode, String name,
                     String specJson, BigDecimal price, BigDecimal marketPrice,
                     String image, Integer status) {
}