package com.zhe.mall.order.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderStatusLog {

    private Long id;

    private String orderNo;

    private Integer fromStatus;

    private Integer toStatus;

    private String operator;

    private String remark;

    private LocalDateTime createdAt;
}
