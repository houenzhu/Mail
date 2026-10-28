package com.zhe.mall.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 退款申请入参（28.7）。 */
public record RefundApplyRequest(@NotBlank(message = "orderNo 不能为空") String orderNo,

                                 @Size(max = 128, message = "退款原因长度不能超过 128") String reason) {
}
