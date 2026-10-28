package com.zhe.mall.order.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderItem {

    private Long id;

    private String orderNo;

    private Long userId;

    private Long skuId;

    private Long productId;

    private String productName;

    private String skuSpec;

    private BigDecimal price;

    private Integer qty;

    private BigDecimal totalAmount;

    private LocalDateTime createdAt;
}
