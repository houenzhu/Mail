package com.zhe.mall.common.auth;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;

/**
 * 当前请求的登录用户上下文（ThreadLocal，由 UserContextFilter 写入与清理）。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long requireUserId() {
        CurrentUser user = HOLDER.get();
        if (user == null || user.userId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.userId();
    }
}
