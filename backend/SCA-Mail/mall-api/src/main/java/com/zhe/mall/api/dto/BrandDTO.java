package com.zhe.mall.api.dto;

/**
 * 品牌（后台下拉用）。
 */
public record BrandDTO(Long id, String name, String logoUrl, Integer sortNo, Integer status) {
}
