package com.zhe.mall.product.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductSku {

    private Long id;

    private Long productId;

    private String skuCode;

    private String name;

    private String specJson;

    private BigDecimal price;

    private BigDecimal marketPrice;

    private String image;

    private Integer status;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
