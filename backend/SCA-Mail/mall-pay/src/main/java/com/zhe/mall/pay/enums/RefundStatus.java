package com.zhe.mall.pay.enums;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;

import java.util.Arrays;

/**
 * 退款单状态（16.5）。本阶段不落地 {@link #PENDING_AUDIT}：无审核流，见 28.12 已知限制。
 */
public enum RefundStatus {

    PENDING_AUDIT(0, "待审核"),

    REFUNDING(10, "退款中"),

    SUCCESS(20, "退款成功"),

    FAILED(30, "退款失败");

    private final int code;

    private final String label;

    RefundStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static RefundStatus of(Integer code) {
        if (code == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "退款单状态不能为空");
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_INVALID, "未知的退款单状态: " + code));
    }
}
