package com.zhe.mall.api.dto;

/**
 * 管理端商品列表的 Tab 角标计数（计划文档 8.1）。
 *
 * <p>用一个显式记录而不是 {@code Map<String, Long>}：字段名即契约，
 * 避免前后端对 key 字符串的隐式约定（改个 key 就静默变 null）。
 * {@code deleted} 单独给出，因为它与前三个不是同一维度（前三个只统计未删除商品）。
 */
public record AdminProductStatusCounts(long all, long onSale, long offShelf, long deleted) {
}
