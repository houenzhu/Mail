package com.zhe.mall.common.auth;

import com.zhe.mall.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * {@link RequireStaff} 的校验实现（服务端角色鉴权，见需求文档 21.3）。
 *
 * <p>挂在 {@code HandlerInterceptor} 上：只对标注了注解的控制器/方法生效，其余接口零影响。
 *
 * <p><b>为什么直接写响应而不是抛 {@code BusinessException}</b>：拦截器抛出的异常会绕过
 * {@code @RestControllerAdvice}（GlobalExceptionHandler），无法复用统一异常处理；
 * 而写响应可以像网关一样给出语义正确的 HTTP 状态码（401 未登录 / 403 无权限），
 * 与网关 21.9.2 的映射口径保持一致。
 */
public class RequireStaffInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequireStaffInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireStaff annotation = findAnnotation(handlerMethod);
        if (annotation == null) {
            return true;
        }

        CurrentUser user = UserContext.get();
        if (user == null || user.userId() == null) {
            // 身份头缺失或内部令牌不匹配（直连服务/伪造身份头）→ 视为未登录
            log.warn("后台接口拒绝匿名访问: method={}, path={}", request.getMethod(), request.getRequestURI());
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED, "未登录或登录已过期");
            return false;
        }

        UserRole[] required = annotation.value();
        // 角色判断集中在 CurrentUser.hasRole：那里定义了 ADMIN ⊇ OPERATOR ⊇ CS 的包含关系，
        // 避免"管理员反而没有运营权限"这种荒谬结果在多个调用点各写一遍。
        boolean allowed = required.length == 0
                ? user.isStaff()
                : Arrays.stream(required).anyMatch(role -> user.hasRole(role.name()));

        if (!allowed) {
            log.warn("后台接口拒绝越权访问: userId={}, roles={}, required={}, path={}",
                    user.userId(), user.roles(), Arrays.toString(required), request.getRequestURI());
            writeError(request, response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN, "无后台访问权限");
            return false;
        }
        return true;
    }

    /** 方法级注解优先，其次看控制器类级注解。 */
    private RequireStaff findAnnotation(HandlerMethod handlerMethod) {
        RequireStaff onMethod = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequireStaff.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequireStaff.class);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int httpStatus, ResultCode code, String message) {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        // 手工拼 JSON：与网关错误响应体同形（见 18.1 / 21.9.2），且不依赖具体 Jackson 版本
        String body = "{\"code\":" + code.getCode()
                + ",\"message\":\"" + escape(message)
                + "\",\"data\":null,\"success\":false}";
        try {
            response.getWriter().write(body);
        } catch (IOException e) {
            log.error("写鉴权失败响应时出错: uri={}", request.getRequestURI(), e);
        }
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
