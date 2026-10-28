package com.zhe.mall.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款单返回（28.4 / 28.7）。 */
public record RefundVO(String refundNo,
                       String paymentNo,
                       String orderNo,
                       BigDecimal amount,
                       Integer status,
                       String statusLabel,
                       String reason,
                       String channelRefundNo,
                       LocalDateTime appliedAt,
                       LocalDateTime finishedAt,
                       String failReason,
                       boolean idempotentReplay) {
}
