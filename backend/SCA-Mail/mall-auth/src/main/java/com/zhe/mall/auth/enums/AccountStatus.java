package com.zhe.mall.auth.enums;

/**
 * 账号状态，对应 mall_auth.user_account.status。
 */
public enum AccountStatus {

    DISABLED(0, "已注销"),
    NORMAL(1, "正常"),
    LOCKED(2, "锁定");

    private final int code;

    private final String label;

    AccountStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }
}
