package com.zhe.mall.admin.config;

import com.zhe.mall.common.auth.AuthHeaders;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 出站请求拦截：把网关下发的内部令牌与 traceId 继续透传给下游服务。
 *
 * <p><b>为什么必须透传</b>：各业务服务只信任携带正确 {@code X-Gateway-Token} 的请求
 * （fail-closed，见 ADR-012）。mall-admin 作为 BFF 转发时如果不带上这个头，
 * 下游会把它当成匿名直连，导致 {@code @RequireStaff} 及其他基于身份的逻辑全部失效。
 *
 * <p>同时透传 {@code X-Trace-Id}，让一次后台操作在下游日志里能串成同一条链路（22.2）。
 */
@Configuration
public class AdminFeignConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminFeignConfig.class);

    @Bean
    public RequestInterceptor identityPropagationInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                // 非 Web 线程（如定时任务）发起调用：没有可透传的身份，明确记日志便于排查
                log.debug("Feign 调用不在 Web 请求上下文中，跳过身份透传: {}", template.url());
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            copyHeader(request, template, AuthHeaders.INTERNAL_TOKEN);
            copyHeader(request, template, AuthHeaders.TRACE_ID);
            copyHeader(request, template, AuthHeaders.USER_ID);
            copyHeader(request, template, AuthHeaders.USER_NAME);
            copyHeader(request, template, AuthHeaders.USER_ROLE);
        };
    }

    private void copyHeader(HttpServletRequest request, feign.RequestTemplate template, String header) {
        String value = request.getHeader(header);
        if (value != null && !value.isBlank()) {
            template.header(header, value);
        }
    }
}
