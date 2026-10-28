package com.zhe.mall.api.dto;

/**
 * 分类（后台下拉与分类树用）。
 */
public record CategoryDTO(Long id, Long parentId, String name, Integer level, Integer sortNo, Integer status) {
}
