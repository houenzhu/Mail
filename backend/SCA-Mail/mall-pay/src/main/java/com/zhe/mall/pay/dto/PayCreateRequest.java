package com.zhe.mall.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起支付入参（28.4）。
 *
 * <p>{@code requestId} 是客户端生成的幂等号，与 {@code orderNo} 一起构成
 * {@code payment.uk_order_request}：同一请求重投不会产生第二张支付单。
 */
public record PayCreateRequest(@NotBlank(message = "orderNo 不能为空") String orderNo,

                               @NotNull(message = "payType 不能为空") Integer payType,

                               @NotBlank(message = "requestId 不能为空")
                               @Size(max = 64, message = "requestId 长度不能超过 64") String requestId) {
}
