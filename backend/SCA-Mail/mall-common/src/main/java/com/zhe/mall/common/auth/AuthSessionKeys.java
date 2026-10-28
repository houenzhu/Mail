package com.zhe.mall.common.auth;

/**
 * Sa-Token 账号会话中存放的字段名（见需求文档 21.8.7）。
 *
 * <p>由 mall-auth 登录时写入、mall-gateway 校验时读取，两侧必须一致，故放在公共模块。
 */
public final class AuthSessionKeys {

    /** 用户名（String） */
    public static final String USERNAME = "username";

    /** 角色，逗号分隔（String） */
    public static final String ROLES = "roles";

    private AuthSessionKeys() {
    }
}