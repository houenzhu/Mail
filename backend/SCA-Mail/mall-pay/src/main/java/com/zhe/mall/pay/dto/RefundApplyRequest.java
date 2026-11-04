package com.zhe.mall.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 退款申请入参（28.7 / 12.24）。
 *
 * @param orderNo 订单号
 * @param reason  退款原因
 * @param audit   是否走人工审核。为 {@code true} 时创建「0 待审核」退款单，
 *                由客服/运营在后台同意后才调渠道退款；不传或 {@code false} 保持
 *                原有「申请即退款」的即时语义（C 端与既有 E2E 契约不变）。
 */
public record RefundApplyRequest(@NotBlank(message = "orderNo 不能为空") String orderNo,

                                 @Size(max = 128, message = "退款原因长度不能超过 128") String reason,

                                 Boolean audit) {

    public boolean auditRequested() {
        return Boolean.TRUE.equals(audit);
    }
}
