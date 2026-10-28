package com.zhe.mall.auth.dto;

import java.util.List;

/**
 * 登录 / 刷新的统一返回：令牌对与有效期（accessToken 默认 30min，refreshToken 默认 7d，见 21.1）。
 */
public record LoginVO(Long userId, String username, String nickname, List<String> roles,
                      String tokenType, String accessToken, long expiresIn,
                      String refreshToken, long refreshExpiresIn) {
}
