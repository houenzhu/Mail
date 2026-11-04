package com.zhe.mall.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 看板趋势的单日数据点。
 *
 * <p>没有成交的日期也必须返回（GMV=0、订单=0），否则前端折线会被"跳日"拉歪，
 * 运营也会误读为数据缺失。补齐逻辑在服务层完成。
 *
 * @param date       日期
 * @param gmv        当日 GMV（按 paid_at 归日）
 * @param orders     当日下单数（按 created_at 归日）
 * @param paidOrders 当日支付成功订单数（按 paid_at 归日）
 */
public record AdminDashboardTrendPoint(
        LocalDate date,
        BigDecimal gmv,
        long orders,
        long paidOrders) {
}
