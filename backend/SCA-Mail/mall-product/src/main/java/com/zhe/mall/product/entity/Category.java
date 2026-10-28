package com.zhe.mall.product.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 商品分类（表 category，见 sql/01_mall_product.sql）。 */
@Data
public class Category {

    private Long id;

    private Long parentId;

    private String name;

    private Integer level;

    private Integer sortNo;

    private Integer status;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
