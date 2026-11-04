package com.zhe.mall.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台退款列表的一行（staff 专用契约）。
 *
 * <p>包含发起方与操作人：退款是资金操作，后台必须能看出"谁发起的、谁处理的"。
 * 审核人与发起人分列：审核不会覆盖 {@code operator}，两条审计链都要保留。
 */
public record AdminRefundRow(
        String refundNo,
        String paymentNo,
        String orderNo,
        Long userId,
        BigDecimal amount,
        Integer status,
        String statusLabel,
        String reason,
        String operator,
        Long operatorId,
        String auditedBy,
        Long auditedById,
        java.time.LocalDateTime auditedAt,
        String channelRefundNo,
        LocalDateTime appliedAt,
        LocalDateTime finishedAt,
        String failReason,
        LocalDateTime createdAt) {
}
