package com.zhe.mall.cart.dto;

import java.math.BigDecimal;

/** 结算预览行：直接映射订单服务的 items（skuId + qty），并带实时单价供前端展示。 */
public record CartCheckoutItem(Long skuId, String productName, String skuName,
                               BigDecimal price, Integer quantity) {
}
