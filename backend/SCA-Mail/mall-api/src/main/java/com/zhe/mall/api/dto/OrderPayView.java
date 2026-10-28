package com.zhe.mall.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付视图（mall-pay 视角的订单快照）。
 *
 * <p>刻意不复用订单侧的 OrderDTO：支付侧只关心「谁的单、什么状态、付多少、什么时候过期」，
 * 多带字段会让服务边界变模糊（见 17.2 数据所有权矩阵）。
 */
public record OrderPayView(String orderNo,
                           Long userId,
                           Integer status,
                           String statusLabel,
                           BigDecimal payAmount,
                           Integer payType,
                           LocalDateTime expireAt) {
}
