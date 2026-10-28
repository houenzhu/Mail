package com.zhe.mall.pay.dto;

/** 回调处理结果（渠道侧只看 HTTP 200 与幂等语义，本 VO 供排障与 E2E 断言）。 */
public record CallbackResultVO(String paymentNo,
                               Integer status,
                               String statusLabel,
                               String orderNo,
                               Integer orderStatus,
                               String channelTradeNo,
                               boolean idempotentReplay) {
}
