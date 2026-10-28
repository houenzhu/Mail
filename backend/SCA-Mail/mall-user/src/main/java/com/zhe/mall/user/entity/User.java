package com.zhe.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private Integer level;

    private Integer growth;

    private Integer points;

    private Integer status;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}