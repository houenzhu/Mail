package com.zhe.mall.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图（C 端）。
 *
 * <p>收货信息与物流信息是订单快照的一部分（BR-09）：用户需要在订单详情里看到
 * "这单发给谁、寄到哪、走的哪个快递"，因此必须放在主视图而不是另开内部接口。
 *
 * <p>userId 保留是因为后台/客服排障要用，但 C 端页面不展示，也不允许用它做归属判断
 * （归属一律以服务端 {@code requireOwnership} 为准）。
 */
public record OrderDTO(String orderNo, Long userId, Integer status, String statusLabel,
                       BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal payAmount,
                       Long couponId, Integer payType, String cancelReason,
                       String receiverName, String receiverPhone, String receiverAddress,
                       String remark, String shippingCompany, String trackingNo,
                       LocalDateTime shippedAt, String shippedBy, LocalDateTime cancelledAt,
                       LocalDateTime expireAt, LocalDateTime paidAt, LocalDateTime completedAt,
                       LocalDateTime createdAt) {
}
