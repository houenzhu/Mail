package com.zhe.mall.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 从网关注入的请求头解析当前登录用户。
 *
 * <p>安全约束：只有携带正确内部令牌（{@link AuthHeaders#INTERNAL_TOKEN}）的请求才会被信任；
 * 直连服务且未携带令牌时，身份头一律忽略（视为匿名），避免伪造 {@code X-User-Id} 越权。
 */
public class UserContextFilter extends OncePerRequestFilter {

    private static final String ROLE_SEPARATOR = ",";

    private static final String MDC_TRACE_ID = "traceId";

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private final InternalTokenVerifier internalTokenVerifier;

    public UserContextFilter(InternalTokenVerifier internalTokenVerifier) {
        this.internalTokenVerifier = internalTokenVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UserContext.clear();
        String traceId = request.getHeader(AuthHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(AuthHeaders.TRACE_ID, traceId);
        try {
            CurrentUser user = resolveUser(request);
            if (user != null) {
                UserContext.set(user);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private CurrentUser resolveUser(HttpServletRequest request) {
        if (!trusted(request)) {
            return null;
        }
        String userId = request.getHeader(AuthHeaders.USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        Long parsedUserId;
        try {
            parsedUserId = Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return new CurrentUser(parsedUserId, request.getHeader(AuthHeaders.USER_NAME), parseRoles(request));
    }

    /**
     * fail-closed：内部令牌未配置时**不信任任何身份头**（视为匿名），
     * 避免某个服务漏配 {@code mall.auth.internal-token} 就变成"谁都能伪造 X-User-Id"（见 21.2 / ADR-012）。
     */
    private boolean trusted(HttpServletRequest request) {
        return internalTokenVerifier.trusted(request.getHeader(AuthHeaders.INTERNAL_TOKEN));
    }

    private List<String> parseRoles(HttpServletRequest request) {
        String roles = request.getHeader(AuthHeaders.USER_ROLE);
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(ROLE_SEPARATOR)).map(String::trim).filter(role -> !role.isEmpty()).toList();
    }
}
