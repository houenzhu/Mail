package com.zhe.mall.inventory.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryRecord {

    private Long id;

    private Long skuId;

    private String orderNo;

    private Integer type;

    private Integer qty;

    private Integer status;

    private String remark;

    private LocalDateTime createdAt;
}
