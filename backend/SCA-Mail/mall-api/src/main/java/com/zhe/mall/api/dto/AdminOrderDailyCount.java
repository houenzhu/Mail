package com.zhe.mall.api.dto;

import java.time.LocalDate;

/**
 * 订单按日下单数（按 created_at 归日，含未支付）。
 */
public record AdminOrderDailyCount(LocalDate date, long orders) {
}
