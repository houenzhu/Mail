package com.zhe.mall.pay.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账差异清单（28.3.4）。 */
@Data
public class PayReconcileDiff {

    private Long id;

    private LocalDate bizDate;

    private String paymentNo;

    private String channelTradeNo;

    private String diffType;

    private String detail;

    private Integer status;

    private LocalDateTime createdAt;
}
