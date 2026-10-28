package com.zhe.mall.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 网关跨域配置（CORS）。
 *
 * <p><b>为什么必须显式配置，且必须用 WebFilter</b>：需求文档 21.9.5 已预警——
 * {@code SaReactorFilter} 也是 {@code WebFilter}，优先级最低；浏览器发起的跨域请求会**先**发
 * {@code OPTIONS} 预检，如果预检走到鉴权过滤器，就会因为没有令牌被拦成 **401**。
 * 前端看到的报错是 401 而不是 CORS，排查成本极高。
 *
 * <p>这里把 {@link CorsWebFilter} 的 order 设为 {@link Ordered#HIGHEST_PRECEDENCE}，
 * 让它在鉴权过滤器之前短路掉预检请求（Spring 的 CorsWebFilter 对预检直接返回并写入
 * {@code Access-Control-Allow-*} 响应头，不再往下走过滤器链）。同时在
 * {@code mall.auth.whitelist} 里保留 {@code OPTIONS:/**} 作为第二道保障。
 *
 * <p>生产环境请用 {@code CORS_ALLOWED_ORIGIN_PATTERNS} 收敛为具体域名，
 * 不要长期保留 {@code *}。
 */
@Configuration
public class GatewayCorsConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayCorsConfig.class);

    /**
     * 顺序很关键：必须是最高优先级，让预检请求在鉴权过滤器之前就被短路。
     * {@code CorsWebFilter} 本身没有 setOrder，因此用 bean 方法上的 {@code @Order} 指定。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter(
            @Value("${mall.auth.cors.allowed-origin-patterns:*}") String allowedOrigins,
            @Value("${mall.auth.cors.allow-credentials:true}") boolean allowCredentials) {

        List<String> originPatterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        // 用 originPatterns 而不是 origins：允许携带凭据时，origins 不能是 "*"（Spring 会直接抛异常），
        // 而 originPatterns 支持 "*" 且能正确回显来源域。
        config.setAllowedOriginPatterns(originPatterns.isEmpty() ? List.of("*") : originPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        // 用 * 放开请求头，覆盖 Authorization / Content-Type / X-Gateway-Token / X-Trace-Id；
        // allowCredentials=true 时 Spring 会回显实际请求的头部而不是字面 "*"。
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("网关 CORS 初始化: originPatterns={}, allowCredentials={}", originPatterns, allowCredentials);

        return new CorsWebFilter(source);
    }
}
