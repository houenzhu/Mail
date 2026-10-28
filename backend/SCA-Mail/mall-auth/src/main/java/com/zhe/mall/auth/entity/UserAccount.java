package com.zhe.mall.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号凭证（mall_auth.user_account）。
 */
@Data
public class UserAccount {

    private Long userId;

    private String username;

    private String phone;

    private String phoneHash;

    private String passwordHash;

    private String nickname;

    /** 角色，逗号分隔：USER / OPERATOR / CS / ADMIN */
    private String roles;

    private Integer status;

    private Integer failCount;

    private LocalDateTime lockUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
