package com.zhe.mall.gateway.filter;

import com.zhe.mall.common.auth.AuthHeaders;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.gateway.config.GatewayAuthProperties;
import com.zhe.mall.gateway.support.GatewayAuthAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 网关身份透传（见需求文档 21.2 / 21.8.7）。
 *
 * <ol>
 *   <li>无条件剥离客户端传入的身份头，杜绝伪造 {@code X-User-Id} 越权；</li>
 *   <li>读取鉴权过滤器落在 exchange 属性上的身份（<b>本类不调用 StpUtil</b>，规避 reactor thread-local 限制）；</li>
 *   <li>注入身份头与内部令牌，并生成 X-Trace-Id 向下游透传；</li>
 *   <li>缺少鉴权标记时 fail-closed，避免鉴权过滤器失效被静默放行。</li>
 * </ol>
 */
@Component
public class IdentityPropagationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IdentityPropagationGlobalFilter.class);

    private final String internalToken;

    public IdentityPropagationGlobalFilter(GatewayAuthProperties properties) {
        this.internalToken = properties.getInternalToken();
        log.info("网关身份透传初始化: 内部令牌已配置={}", internalToken != null && !internalToken.isBlank());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        ServerHttpRequest cleaned = stripClientHeaders(exchange.getRequest());
        Object authRequired = exchange.getAttribute(GatewayAuthAttributes.AUTH_REQUIRED);

        if (authRequired == null) {
            log.error("鉴权过滤器未生效（exchange 缺少 {} 属性），拒绝请求: {}",
                    GatewayAuthAttributes.AUTH_REQUIRED, path);
            return writeError(exchange, ResultCode.UNAUTHORIZED.getCode(), "网关鉴权未初始化");
        }
        if (!Boolean.TRUE.equals(authRequired)) {
            return chain.filter(exchange.mutate().request(withTraceId(cleaned)).build());
        }

        Object userId = exchange.getAttribute(GatewayAuthAttributes.USER_ID);
        if (userId == null) {
            log.error("请求需登录但缺少身份属性，拒绝请求: {}", path);
            return writeError(exchange, ResultCode.UNAUTHORIZED.getCode(), "网关身份解析失败");
        }
        Object username = exchange.getAttribute(GatewayAuthAttributes.USERNAME);
        Object roles = exchange.getAttribute(GatewayAuthAttributes.ROLES);

        ServerHttpRequest authenticated = cleaned.mutate()
                .header(AuthHeaders.USER_ID, String.valueOf(userId))
                .header(AuthHeaders.USER_NAME, username == null ? "" : String.valueOf(username))
                .header(AuthHeaders.USER_ROLE, roles == null ? "" : String.valueOf(roles))
                .header(AuthHeaders.INTERNAL_TOKEN, internalToken)
                .build();
        return chain.filter(exchange.mutate().request(withTraceId(authenticated)).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private ServerHttpRequest stripClientHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(AuthHeaders.USER_ID);
                    headers.remove(AuthHeaders.USER_NAME);
                    headers.remove(AuthHeaders.USER_ROLE);
                    headers.remove(AuthHeaders.INTERNAL_TOKEN);
                })
                .build();
    }

    /**
     * 覆盖客户端传入的 X-Trace-Id（18.1 约定：客户端传的会被覆盖），保证链路起点唯一。
     */
    private ServerHttpRequest withTraceId(ServerHttpRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        return request.mutate()
                .headers(headers -> headers.set(AuthHeaders.TRACE_ID, traceId))
                .build();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        String body = "{\"code\":" + code + ",\"message\":\"" + escape(message) + "\",\"data\":null,\"success\":false}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String escape(String message) {
        return message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}