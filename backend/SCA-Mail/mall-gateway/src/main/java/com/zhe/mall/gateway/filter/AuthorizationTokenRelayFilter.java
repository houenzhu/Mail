package com.zhe.mall.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 把 {@code Authorization: Bearer <token>} 转发成 Sa-Token 读取的令牌头（见需求文档 18.1 / 21.8.7）。
 *
 * <p>Sa-Token 的 {@code token-name} 既是令牌头名，也是 Redis key 命名空间
 * （{@code {token-name}:{loginType}:token:{值}}），因此不能为了兼容 {@code Authorization} 而改它
 * ——改了就与 mall-auth 写入的会话 key 不一致，网关会一直判定令牌无效。
 * 故对外契约不变，由本过滤器在鉴权之前做一次请求头转发。
 *
 * <p>顺序必须早于 Sa-Token 的鉴权过滤器（后者为默认最低优先级）。
 */
@Component
public class AuthorizationTokenRelayFilter implements WebFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String tokenHeaderName;

    public AuthorizationTokenRelayFilter(@Value("${sa-token.token-name:satoken}") String tokenHeaderName) {
        this.tokenHeaderName = tokenHeaderName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getHeaders().getFirst(tokenHeaderName) != null) {
            return chain.filter(exchange);
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return chain.filter(exchange);
        }
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return chain.filter(exchange);
        }
        String relayed = BEARER_PREFIX + value.substring(BEARER_PREFIX.length()).trim();
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> headers.set(tokenHeaderName, relayed))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }
}