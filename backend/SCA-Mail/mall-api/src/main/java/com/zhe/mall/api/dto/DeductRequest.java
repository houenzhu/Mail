package com.zhe.mall.api.dto;

public record DeductRequest(String orderNo, Long skuId, Integer qty) {
}
