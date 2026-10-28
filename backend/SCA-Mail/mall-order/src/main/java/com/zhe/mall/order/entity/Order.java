package com.zhe.mall.order.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {

    private String orderNo;

    private Long userId;

    private String requestId;

    private Integer status;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private Long couponId;

    private Integer payType;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private LocalDateTime expireAt;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    private String cancelReason;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
