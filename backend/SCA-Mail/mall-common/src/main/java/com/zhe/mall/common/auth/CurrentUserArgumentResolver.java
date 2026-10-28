package com.zhe.mall.common.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 支持 Controller 中使用 {@code @CurrentUserId Long userId} 与 {@code CurrentUser currentUser} 参数。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(CurrentUserId.class)) {
            return Long.class.equals(parameter.getParameterType());
        }
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (CurrentUser.class.equals(parameter.getParameterType())) {
            return UserContext.get();
        }
        return UserContext.requireUserId();
    }
}
