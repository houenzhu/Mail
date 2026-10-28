package com.zhe.mall.product.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 商品品牌（表 brand，见 sql/01_mall_product.sql）。 */
@Data
public class Brand {

    private Long id;

    private String name;

    private String logoUrl;

    private String description;

    private Integer sortNo;

    private Integer status;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
