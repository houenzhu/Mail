package com.zhe.mall.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前登录用户信息（手机号脱敏）。
 */
public record UserInfoVO(Long userId, String username, String nickname, String phone,
                         List<String> roles, Integer status, LocalDateTime lastLoginAt) {
}
