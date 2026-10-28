package com.zhe.mall.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderCreateRequest(
        @NotBlank(message = "requestId 不能为空") String requestId,
        @NotBlank(message = "收货人不能为空") String receiverName,
        @NotBlank(message = "收货电话不能为空") String receiverPhone,
        @NotBlank(message = "收货地址不能为空") String receiverAddress,
        String remark,
        Long couponId,
        Integer payType,
        @NotEmpty(message = "订单明细不能为空") @Valid List<OrderItemRequest> items) {
}
