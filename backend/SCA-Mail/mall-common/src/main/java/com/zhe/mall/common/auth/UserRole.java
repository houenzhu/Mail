package com.zhe.mall.common.auth;

/**
 * 角色定义（见需求文档 16.1）。
 */
public enum UserRole {

    BUYER("买家"),

    CS("客服"),

    OPERATOR("运营"),

    ADMIN("管理员");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
