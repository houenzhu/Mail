package com.zhe.mall.gateway.support;

/**
 * 鉴权 WebFilter（SaReactorFilter）与身份透传 GlobalFilter 之间的交换属性（见需求文档 21.8.7）。
 *
 * <p>Sa-Token 的 reactor 上下文是 thread-local，网关 GlobalFilter 内不可直接调用 {@code StpUtil}；
 * 因此会话校验统一在 WebFilter 内完成，身份经 exchange 属性交给 GlobalFilter 注入请求头。
 */
public final class GatewayAuthAttributes {

    /** 本请求是否需要登录身份（Boolean）；属性缺失表示 WebFilter 未生效，GlobalFilter 按 fail-closed 处理 */
    public static final String AUTH_REQUIRED = "mall.auth.required";

    /** 已登录用户 ID（Long） */
    public static final String USER_ID = "mall.auth.userId";

    /** 已登录用户名（String） */
    public static final String USERNAME = "mall.auth.username";

    /** 角色，逗号分隔（String） */
    public static final String ROLES = "mall.auth.roles";

    private GatewayAuthAttributes() {
    }
}
