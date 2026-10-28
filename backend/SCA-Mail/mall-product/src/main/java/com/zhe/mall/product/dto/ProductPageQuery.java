package com.zhe.mall.product.dto;

import lombok.Data;

/**
 * 商品分页条件。
 *
 * <p>{@code deleted} 与 {@code productId} 供管理端使用（C 端不传，走默认 deleted=0）：
 * 这样管理端与 C 端共用同一条 SQL 与同一套字段，避免再维护一份近乎重复的管理端查询
 * （见计划文档 G-02）。
 */
@Data
public class ProductPageQuery {

    private Long productId;

    private Long categoryId;

    private Long brandId;

    private String keyword;

    private Integer status;

    /** 逻辑删除标记：null 视为 0（未删除）。管理端查"已删除"传 1。 */
    private Integer deleted;

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    public int offset() {
        return (pageNo() - 1) * pageSize();
    }

    public int pageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int pageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
