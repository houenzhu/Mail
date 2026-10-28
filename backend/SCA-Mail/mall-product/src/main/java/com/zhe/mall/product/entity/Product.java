package com.zhe.mall.product.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {

    private Long id;

    private String name;

    private String subtitle;

    private Long categoryId;

    private Long brandId;

    private String mainImage;

    private BigDecimal priceMin;

    private BigDecimal priceMax;

    private Integer sales;

    private String detail;

    private Integer status;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
