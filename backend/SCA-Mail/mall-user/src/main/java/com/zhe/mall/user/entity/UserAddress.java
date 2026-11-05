package com.zhe.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 收货地址（UC-USER-02）。归属校验一律以 userId 为准，不信任请求体。 */
@Data
public class UserAddress {

    private Long id;

    private Long userId;

    private String receiverName;

    private String receiverPhone;

    private String region;

    private String detailAddress;

    private Integer isDefault;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
