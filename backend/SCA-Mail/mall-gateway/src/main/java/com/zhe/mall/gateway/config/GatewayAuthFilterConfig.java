package com.zhe.mall.gateway.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.zhe.mall.common.auth.AuthSessionKeys;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.gateway.support.GatewayAuthAttributes;
import com.zhe.mall.gateway.support.GatewayPathRules;
import com.zhe.mall.gateway.support.InternalPathAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * 网关鉴权过滤器（见需求文档 21.2 / 21.8.7）。
 *
 * <p>由 Sa-Token 的 {@link SaReactorFilter} 承担会话校验：它是 {@code WebFilter}，先于 Spring Cloud Gateway
 * 的 GlobalFilter 执行，因此 {@code StpUtil} 的 reactor 上下文在此可用。校验通过后把身份写入
 * {@code SaHolder.getStorage()}（落点为 exchange 属性），由
 * {@link com.zhe.mall.gateway.filter.IdentityPropagationGlobalFilter} 注入请求头。
 *
 * <p>顺序沿用迁移前的口径：<b>内部路径拦截 → 白名单放行 → 会话校验</b>。
 */
@Configuration
public class GatewayAuthFilterConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthFilterConfig.class);

    /**
     * 启动自检：确认 Sa-Token 会话真的落在 Redis（而不是退化为内存实现，那会导致多实例互不认账）。
     */
    @Bean
    public ApplicationRunner saTokenStorageSelfCheck(ObjectProvider<StringRedisTemplate> redisProvider) {
        return args -> {
            Object dao = SaManager.getSaTokenDao();
            String daoClass = dao == null ? "null" : dao.getClass().getName();
            log.info("Sa-Token 会话存储: dao={}, Redis 实现={}, 阻塞式 StringRedisTemplate 已装配={}",
                    daoClass, dao instanceof cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate,
                    redisProvider.getIfAvailable() != null);
        };
    }

    @Bean
    public SaReactorFilter saTokenAuthFilter(GatewayAuthProperties properties) {
        log.info("网关鉴权初始化（Sa-Token）: enabled={}, 白名单 {} 条",
                properties.isEnabled(), properties.getWhitelist().size());
        return new SaReactorFilter()
                .addInclude("/**")
                .setAuth(obj -> authenticate(properties))
                .setError(GatewayAuthFilterConfig::writeError);
    }

    private static void authenticate(GatewayAuthProperties properties) {
        SaRequest request = SaHolder.getRequest();
        String path = request.getRequestPath();
        String method = request.getMethod();
        // 注意：这里必须用 SaReactorSyncHolder 取 exchange，不能用 SaHolder.getStorage()
        // —— 后者在网关的过滤器链里会拿到 exchange 为 null 的包装对象（实测 NPE）。
        Map<String, Object> attributes = SaReactorSyncHolder.getExchange().getAttributes();

        if (!properties.isEnabled()) {
            log.warn("网关鉴权已关闭（mall.auth.enabled=false），仅限本地调试: {}", path);
            attributes.put(GatewayAuthAttributes.AUTH_REQUIRED, false);
            return;
        }
        if (GatewayPathRules.isInternalPath(path)) {
            log.warn("网关拦截内部接口的外部访问: method={}, path={}", method, path);
            throw new InternalPathAccessDeniedException(path);
        }
        if (GatewayPathRules.isWhitelisted(properties.getWhitelist(), method, path)) {
            attributes.put(GatewayAuthAttributes.AUTH_REQUIRED, false);
            return;
        }

        // 未登录/令牌无效/过期/被踢 均由此抛出 NotLoginException，统一在 writeError 内映射
        StpUtil.checkLogin();
        SaSession session = StpUtil.getSession();
        attributes.put(GatewayAuthAttributes.AUTH_REQUIRED, true);
        attributes.put(GatewayAuthAttributes.USER_ID, StpUtil.getLoginIdAsLong());
        // exchange 的属性表是 ConcurrentHashMap，不接受 null：会话里缺字段时按空串落库
        putIfPresent(attributes, GatewayAuthAttributes.USERNAME,
                session.getString(AuthSessionKeys.USERNAME));
        putIfPresent(attributes, GatewayAuthAttributes.ROLES,
                session.getString(AuthSessionKeys.ROLES));
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, String value) {
        attributes.put(key, value == null ? "" : value);
    }

    /**
     * 错误响应：必须显式 {@code setStatus}，否则 Sa-Token 默认返回 HTTP 200（21.8.4 问题 4 的规避）。
     */
    private static Object writeError(Throwable error) {
        SaResponse response = SaHolder.getResponse();
        response.setHeader("Content-Type", "application/json;charset=UTF-8");

        if (error instanceof InternalPathAccessDeniedException) {
            response.setStatus(403);
            return body(ResultCode.FORBIDDEN.getCode(), "内部接口不允许外部访问");
        }
        if (error instanceof NotLoginException notLogin) {
            log.warn("网关鉴权失败: type={}, message={}", notLogin.getType(), notLogin.getMessage());
            response.setStatus(401);
            return switch (notLogin.getType()) {
                case NotLoginException.NOT_TOKEN -> body(ResultCode.UNAUTHORIZED.getCode(), "未携带访问令牌");
                case NotLoginException.NO_PREFIX -> body(ResultCode.UNAUTHORIZED.getCode(), "令牌前缀不正确，应为 Bearer");
                case NotLoginException.TOKEN_TIMEOUT -> body(ResultCode.TOKEN_EXPIRED.getCode(), "登录已过期，请重新登录");
                case NotLoginException.BE_REPLACED -> body(ResultCode.TOKEN_EXPIRED.getCode(), "账号已在其它设备登录");
                case NotLoginException.KICK_OUT -> body(ResultCode.TOKEN_EXPIRED.getCode(), "账号已被踢下线");
                case NotLoginException.TOKEN_FREEZE -> body(ResultCode.TOKEN_EXPIRED.getCode(), "令牌已被冻结");
                default -> body(ResultCode.UNAUTHORIZED.getCode(), "令牌无效或已失效");
            };
        }

        log.error("网关鉴权出现未预期异常", error);
        response.setStatus(401);
        return body(ResultCode.UNAUTHORIZED.getCode(), "鉴权失败");
    }

    /**
     * 与迁移前的网关错误响应体保持同形（18.1）。
     *
     * <p>必须返回 JSON 字符串：Sa-Token 对 {@code setError} 的返回值按 {@code String.valueOf} 直接写出，
     * 不会走 JSON 插件序列化（返回 POJO 会得到 Java 的 toString）。
     */
    private static String body(int code, String message) {
        return "{\"code\":" + code + ",\"message\":\"" + escape(message) + "\",\"data\":null,\"success\":false}";
    }

    private static String escape(String message) {
        return message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
