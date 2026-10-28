package com.zhe.mall.inventory.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Inventory {

    private Long skuId;

    private Integer total;

    private Integer locked;

    private Integer available;

    private Integer warnThreshold;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
