package com.zhe.mall.pay.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付单（28.3.1）。 */
@Data
public class Payment {

    private String paymentNo;

    private String orderNo;

    private Long userId;

    private String requestId;

    private String channel;

    private Integer payType;

    private BigDecimal amount;

    private Integer status;

    private String channelTradeNo;

    private String prepayId;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    private LocalDateTime expireAt;

    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
