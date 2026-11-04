package com.zhe.mall.pay.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款单（28.3.3）。 */
@Data
public class Refund {

    private String refundNo;

    private String paymentNo;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    private Integer status;

    /** 幂等键：处理中/成功为 paymentNo，失败为 null（uk_active 唯一索引） */
    private String activeKey;

    private String reason;

    private String operator;

    private Long operatorId;

    /** 审核人账号（后台 staff）；仅审核流产生，即时退款为 null */
    private String auditedBy;

    /** 审核人用户 ID */
    private Long auditedById;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    private String channelRefundNo;

    private LocalDateTime appliedAt;

    private LocalDateTime finishedAt;

    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
