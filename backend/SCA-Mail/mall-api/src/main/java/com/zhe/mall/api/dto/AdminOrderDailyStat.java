package com.zhe.mall.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单按日聚合的原始行（Mapper 返回，服务层再补零并组装趋势）。
 *
 * @param date       归日
 * @param gmv        当日成交额（按 paid_at 归日，只含已支付及之后的状态）
 * @param paidOrders 当日的支付成功订单数
 */
public record AdminOrderDailyStat(LocalDate date, BigDecimal gmv, long paidOrders) {
}
