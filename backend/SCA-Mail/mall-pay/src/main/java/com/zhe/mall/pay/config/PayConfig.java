package com.zhe.mall.pay.config;

import com.zhe.mall.common.auth.AuthHeaders;
import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付服务装配。
 *
 * <p>关键一点：<b>Feign 调用必须自带内部令牌</b>。mall-pay → mall-order 是直连（不经网关），
 * 网关的 {@code IdentityPropagationGlobalFilter} 不会参与，因此令牌只能由调用方自己加（ADR-022）。
 */
@Configuration
public class PayConfig {

    private static final Logger log = LoggerFactory.getLogger(PayConfig.class);

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(
            @Value("${mall.auth.internal-token:}") String internalToken) {
        log.info("支付服务内部令牌已配置={}（订单内部回调依赖该令牌，见 ADR-022）",
                internalToken != null && !internalToken.isBlank());
        return template -> template.header(AuthHeaders.INTERNAL_TOKEN, internalToken);
    }
}
