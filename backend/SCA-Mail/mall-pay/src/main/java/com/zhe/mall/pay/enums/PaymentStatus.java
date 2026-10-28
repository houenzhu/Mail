package com.zhe.mall.pay.enums;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;

import java.util.Arrays;

/**
 * 支付单状态（16.5 状态字典），code 与 {@code payment.status} 一一对应。
 */
public enum PaymentStatus {

    /** 已建单，渠道还没返回 */
    CREATED(0, "待支付"),

    /** 渠道下单成功，等待支付结果（回调或查询） */
    PAYING(10, "支付中"),

    /** 支付成功 */
    SUCCESS(20, "支付成功"),

    /** 渠道返回失败 */
    FAILED(30, "支付失败"),

    /** 关闭（重复发起 / 订单取消 / 超时） */
    CLOSED(40, "已关闭"),

    /** 已退款 */
    REFUNDED(50, "已退款");

    private final int code;

    private final String label;

    PaymentStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static PaymentStatus of(Integer code) {
        if (code == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "支付单状态不能为空");
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_INVALID, "未知的支付单状态: " + code));
    }
}
