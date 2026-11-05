package com.zhe.mall.cart.config;

import com.zhe.mall.common.auth.AuthHeaders;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 购物车调用商品 / 库存时的内部凭据透传。
 *
 * <p>mall-cart → mall-product / mall-inventory 是服务直连，不经过网关，
 * 因此网关的 {@code IdentityPropagationGlobalFilter} 不会参与。
 * 下游服务对身份头是 fail-closed 的，不主动携带 {@code X-Gateway-Token} 就会被当成匿名直连。
 */
@Configuration
public class CartFeignConfig {

    private static final Logger log = LoggerFactory.getLogger(CartFeignConfig.class);

    @Bean
    public RequestInterceptor cartIdentityPropagationInterceptor(
            @Value("${mall.auth.internal-token:}") String internalToken) {
        log.info("购物车内部令牌已配置={}", internalToken != null && !internalToken.isBlank());
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header(AuthHeaders.INTERNAL_TOKEN, internalToken);
            }
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
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
