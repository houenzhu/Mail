package com.zhe.mall.order.dto;

import java.math.BigDecimal;

public record OrderItemDTO(Long id, Long skuId, Long productId, String productName,
                           String skuSpec, BigDecimal price, Integer qty, BigDecimal totalAmount) {
}
