package com.zhe.mall.order.enums;

/**
 * 状态流转操作者，落库为 order_status_log.operator。
 */
public enum OrderOperator {

    USER("user", "用户"),

    SYSTEM("system", "系统"),

    PAY("pay", "支付"),

    MQ("mq", "消息"),

    /** 后台运营/管理员操作（发货等），操作人身份来自网关注入的可信头。 */
    ADMIN("admin", "后台");

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
