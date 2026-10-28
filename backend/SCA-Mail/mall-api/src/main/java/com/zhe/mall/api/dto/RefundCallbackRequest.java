package com.zhe.mall.api.dto;

import java.math.BigDecimal;

/**
 * 退款阶段回调入参（mall-pay → mall-order，内部令牌调用）。
 *
 * <p>订单侧只认 {@link RefundStage} 三个阶段，不复制支付侧的退款状态机（28.5）。
 */
public record RefundCallbackRequest(String refundNo, RefundStage stage, BigDecimal amount, String reason) {
}
