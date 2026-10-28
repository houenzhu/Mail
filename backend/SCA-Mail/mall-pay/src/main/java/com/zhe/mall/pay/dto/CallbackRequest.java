package com.zhe.mall.pay.dto;

import java.math.BigDecimal;

/**
 * 渠道异步回调报文（28.4）。
 *
 * <p>{@code status} 只允许 {@code SUCCESS} / {@code FAIL}：其余值一律按失败处理并告警，
 * 防止渠道新增枚举时被"未知状态当成功"（28.6 纪律 2）。
 *
 * <p>刻意不加 {@code @NotBlank} 校验：回调缺字段时也要落流水留痕（28.6 第 1 步），
 * 由 {@code PaymentService} 显式判定后返回 {@code 10002}，而不是让校验框架在进方法前就抛错。
 */
public record CallbackRequest(String paymentNo,
                              String channelTradeNo,
                              BigDecimal amount,
                              String status,
                              Integer payType,
                              Long timestamp,
                              String sign) {
}
