package com.zhe.mall.order.enums;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;

import java.util.Arrays;

/**
 * 订单状态机状态定义，code 与 orders.status 一一对应，不存中文。
 */
public enum OrderStatus {

    CREATED(10, "待支付"),

    PAID(20, "已支付"),

    SHIPPED(30, "已发货"),

    COMPLETED(40, "已完成"),

    CANCELLED(50, "已取消"),

    REFUNDING(60, "退款中"),

    REFUNDED(70, "已退款");

    private final int code;

    private final String label;

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static OrderStatus of(Integer code) {
        if (code == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "订单状态不能为空");
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_INVALID, "未知的订单状态: " + code));
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED;
    }
}
