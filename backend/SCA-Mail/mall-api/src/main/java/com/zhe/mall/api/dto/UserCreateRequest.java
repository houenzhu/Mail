package com.zhe.mall.api.dto;

/**
 * 内部建档请求：由 mall-auth 在注册时调用，档案以 id 为主键，重复调用幂等（见需求文档 26.6）。
 */
public record UserCreateRequest(Long id, String username, String nickname, String phone) {
}