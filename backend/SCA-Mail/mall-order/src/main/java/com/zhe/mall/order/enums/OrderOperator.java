package com.zhe.mall.order.enums;

/**
 * 状态流转操作者，落库为 order_status_log.operator。
 */
public enum OrderOperator {

    USER("user", "用户"),

    SYSTEM("system", "系统"),

    PAY("pay", "支付"),

    MQ("mq", "消息");

    private final String code;

    private final String label;

    OrderOperator(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
