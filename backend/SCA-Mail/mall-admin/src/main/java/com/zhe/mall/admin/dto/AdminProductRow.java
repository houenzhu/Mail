package com.zhe.mall.admin.dto;

import java.math.BigDecimal;

/**
 * 管理端商品列表的一行（BFF 聚合结果）。
 *
 * <p>字段对应「我的商品」表格的列（计划文档 8.1）：
 * 商品图/名称/ID/规格数/分类 · 价格区间 · 库存 · 累计销量 · 30 日销量 · 创建/更新时间。
 *
 * <p>其中 {@code stock} / {@code skuCount} / {@code sales30d} 需要跨服务或跨库聚合：
 * 商品在 {@code mall_product}、库存在 {@code mall_inventory}（database-per-service，禁止跨库 JOIN），
 * 因此由本 BFF 合并，前端只发一个请求（见计划文档 8.2 的取数方案）。
 */
public record AdminProductRow(
        Long id,
        String name,
        String subtitle,
        String mainImage,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        BigDecimal priceMin,
        BigDecimal priceMax,
        Integer status,
        Integer deleted,
        Integer skuCount,
        Integer stock,
        Integer sales,
        Integer sales30d,
        String createdAt,
        String updatedAt) {
}
