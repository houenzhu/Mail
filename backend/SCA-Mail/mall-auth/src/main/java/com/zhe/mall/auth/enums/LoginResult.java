package com.zhe.mall.auth.enums;

/**
 * 登录结果，对应 mall_auth.login_log.result。
 */
public enum LoginResult {

    SUCCESS(1, "成功"),
    PASSWORD_ERROR(2, "密码错误"),
    LOCKED(3, "账号锁定"),
    ACCOUNT_NOT_FOUND(4, "账号不存在"),
    DISABLED(5, "账号已注销");

    private final int code;

    private final String label;

    LoginResult(int code, String label) {
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
