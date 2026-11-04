package com.zhe.mall.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台订单列表的一行。
 *
 * <p>收货人姓名/电话在这里返回，是因为后台列表要能直接看出"发给谁"。
 * 注意：这是 staff 专用契约，网关已保证只有登录且具备 staff 角色的调用方能到达。
 */
public record AdminOrderRow(
        String orderNo,
        Long userId,
        Integer status,
        String statusLabel,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal payAmount,
        Integer payType,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String remark,
        LocalDateTime expireAt,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        String shippingCompany,
        String trackingNo,
        LocalDateTime shippedAt,
        String shippedBy,
        LocalDateTime createdAt,
        Integer itemCount,
        Integer totalQty) {
}
