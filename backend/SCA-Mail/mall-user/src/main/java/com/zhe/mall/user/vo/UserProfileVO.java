package com.zhe.mall.user.vo;

/**
 * 用户档案视图。手机号按 21.4 脱敏后输出（138****0000），明文不出服务。
 */
public record UserProfileVO(Long id, String username, String nickname, String phone,
                            Integer level, Integer growth, Integer points) {
}