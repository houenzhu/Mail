package com.zhe.mall.pay.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 回调 / 查询流水（28.3.2）。 */
@Data
public class PaymentCallbackLog {

    private Long id;

    private String paymentNo;

    private String channel;

    private String eventType;

    private String channelTradeNo;

    private BigDecimal amount;

    private Integer signValid;

    private Integer processResult;

    private String rawBody;

    private String remoteIp;

    private LocalDateTime createdAt;
}
