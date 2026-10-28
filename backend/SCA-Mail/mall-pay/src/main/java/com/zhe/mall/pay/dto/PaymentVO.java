package com.zhe.mall.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付单查询返回（28.4）。 */
public record PaymentVO(String paymentNo,
                        String orderNo,
                        BigDecimal amount,
                        Integer payType,
                        String channel,
                        Integer status,
                        String statusLabel,
                        String channelTradeNo,
                        LocalDateTime expireAt,
                        LocalDateTime paidAt,
                        LocalDateTime closedAt,
                        String failReason,
                        LocalDateTime createdAt,
                        ChannelParams channelParams) {
}
