package com.zhe.mall.api.dto;

import java.math.BigDecimal;

/**
 * 支付成功回调入参（mall-pay → mall-order，内部令牌调用）。
 *
 * <p>{@code amount} 非空时与订单应付金额比对，防止回调串单（错误码 34004）；
 * 正式签名校验在 mall-pay 侧完成（28.6），订单侧只做金额与状态校验。
 */
public record PayCallbackRequest(String paymentNo, Integer payType, BigDecimal amount) {
}
