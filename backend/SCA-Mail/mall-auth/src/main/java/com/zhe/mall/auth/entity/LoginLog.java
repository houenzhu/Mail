package com.zhe.mall.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录审计（mall_auth.login_log）。
 */
@Data
public class LoginLog {

    private Long id;

    private Long userId;

    private String username;

    private String loginIp;

    private String userAgent;

    private Integer result;

    private LocalDateTime createdAt;
}
