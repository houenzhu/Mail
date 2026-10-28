package com.zhe.mall.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 刷新令牌登记（mall_auth.refresh_token），支持旋转与强制下线（ADR-010）。
 */
@Data
public class RefreshToken {

    private String jti;

    private Long userId;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    private Integer revoked;

    private String rotatedTo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
