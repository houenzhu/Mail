package com.zhe.mall.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 发起支付返回（28.4 字段级契约）。 */
public record PayCreateVO(String paymentNo,
                          String orderNo,
                          BigDecimal amount,
                          Integer payType,
                          String channel,
                          Integer status,
                          String statusLabel,
                          LocalDateTime expireAt,
                          ChannelParams channelParams,
                          boolean idempotentReplay) {
}
