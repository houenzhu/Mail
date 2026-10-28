package com.zhe.mall.api.dto;

import java.math.BigDecimal;

/**
 * 商品（SPU）视图。
 *
 * <p>字段说明（见计划文档 G-02）：{@code categoryId}/{@code brandId} 让管理端能回显与筛选；
 * {@code sales}/{@code createdAt}/{@code updatedAt} 是管理端「我的商品」表格的列。
 * 这些都是商品自身的属性，管理端与 C 端共用同一份记录，不额外复制一套 DTO。
 */
public record ProductDTO(Long id, String name, String subtitle, String mainImage,
                         BigDecimal priceMin, BigDecimal priceMax, Integer status,
                         Long categoryId, Long brandId, Integer sales,
                         String createdAt, String updatedAt) {
}
