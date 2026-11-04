package com.zhe.mall.api.dto;

import java.math.BigDecimal;

/**
 * 看板概览指标（口径见管理后台计划文档 M5）。
 *
 * <p>GMV 只统计**已支付/已发货/已完成/退款中/已退款**订单的 `pay_amount`：
 * 待支付订单还没产生收入，取消订单从未产生收入。退款中/已退款仍计入 GMV，
 * 因为 GMV 表达"成交总额"，退款另由 {@code refunding} 与退款金额体现，不做净额扣减。
 *
 * @param gmvToday      今日 GMV（按 paid_at 落在今天）
 * @param ordersToday   今日订单数（按 created_at 落在今天，含未支付）
 * @param toShip        待发货订单数（status=20）
 * @param refunding     退款中订单数（status=60）
 * @param refundPending 待处理的退款单数（refund.status in (0, 10)：待审核 + 退款中）
 */
public record AdminDashboardOverview(
        BigDecimal gmvToday,
        long ordersToday,
        long toShip,
        long refunding,
        long refundPending) {
}
