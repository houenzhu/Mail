package com.zhe.mall.auth.config;

import com.zhe.mall.common.auth.AuthHeaders;
import feign.RequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证服务装配：口令散列与内部调用凭据透传。
 *
 * <p>访问令牌与刷新令牌都由 Sa-Token 签发，但使用不同的 loginType：
 * 访问令牌走默认的 {@code login}（网关只认它），刷新令牌走 {@code refresh}（见 21.8.7 / ADR-025）。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    /** BCrypt 强度，见 21.4 */
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }


    /**
     * Feign 调用统一携带内部凭据，便于 mall-user 等被调方校验（21.2 / 26.9）。
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(AuthProperties properties) {
        return template -> template.header(AuthHeaders.INTERNAL_TOKEN, properties.getInternalToken());
    }
}