package com.zhe.mall.api.dto;

public record InventoryDTO(Long skuId, Integer total, Integer locked, Integer available) {
}
