package com.zhe.mall.order.dto;

/** 订单明细聚合：列表页显示"几件商品 / 共几件"，避免逐单查明细。 */
public record OrderItemStat(String orderNo, Integer itemCount, Integer totalQty) {
}
