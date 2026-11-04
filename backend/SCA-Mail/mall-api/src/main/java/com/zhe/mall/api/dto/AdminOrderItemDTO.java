package com.zhe.mall.api.dto;

import java.math.BigDecimal;

/** 后台订单明细行（下单时的商品快照，不再回查商品服务）。 */
public record AdminOrderItemDTO(Long skuId, Long productId, String productName, String skuSpec,
                                BigDecimal price, Integer qty, BigDecimal totalAmount) {
}
