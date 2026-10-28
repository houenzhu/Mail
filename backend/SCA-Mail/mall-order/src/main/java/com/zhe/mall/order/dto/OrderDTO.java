package com.zhe.mall.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDTO(String orderNo, Long userId, Integer status, String statusLabel,
                       BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal payAmount,
                       Long couponId, Integer payType, String cancelReason,
                       LocalDateTime expireAt, LocalDateTime paidAt, LocalDateTime createdAt) {
}
