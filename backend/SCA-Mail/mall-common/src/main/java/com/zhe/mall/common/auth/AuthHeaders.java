package com.zhe.mall.common.auth;

/**
 * 网关与服务之间的身份传递头。网关负责写入，服务端只信任携带内部令牌的请求。
 */
public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";

    public static final String USER_NAME = "X-User-Name";

    public static final String USER_ROLE = "X-User-Role";

    public static final String INTERNAL_TOKEN = "X-Gateway-Token";

    public static final String TRACE_ID = "X-Trace-Id";

    private AuthHeaders() {
    }
}
