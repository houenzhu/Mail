package com.zhe.mall.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证配置（见需求文档 22.9）。密钥与盐值生产环境必须由 Nacos 或环境变量提供（26.9）。
 */
@Data
@ConfigurationProperties(prefix = "mall.auth")
public class AuthProperties {

    /** refreshToken 有效期（21.1：7d） */
    private long refreshTtlSeconds = 604800L;

    private String internalToken;

    private String phoneSalt = "mall-dev-phone-salt";

    private LoginFailLock loginFailLock = new LoginFailLock();

    @Data
    public static class LoginFailLock {

        private int maxFailCount = 5;

        private long lockMinutes = 15L;
    }
}