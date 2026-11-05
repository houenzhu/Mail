package com.zhe.mall.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 下单入参。
 *
 * <p>长度上限与 {@code orders} 的列宽一致：不在这里拦住超长字段，
 * 会落到 MySQL 抛 DataTooLong，前端只能看到 500，且事务回滚信息不友好。
 */
public record OrderCreateRequest(
        @NotBlank(message = "requestId 不能为空")
        @Size(max = 64, message = "requestId 长度不能超过 64") String requestId,

        @NotBlank(message = "收货人不能为空")
        @Size(max = 64, message = "收货人不能超过 64 个字符") String receiverName,

        @NotBlank(message = "收货电话不能为空")
        @Size(max = 20, message = "收货电话不能超过 20 个字符") String receiverPhone,

        @NotBlank(message = "收货地址不能为空")
        @Size(max = 255, message = "收货地址不能超过 255 个字符") String receiverAddress,

        @Size(max = 255, message = "备注不能超过 255 个字符") String remark,

        Long couponId,

        Integer payType,

        @NotEmpty(message = "订单明细不能为空") @Valid List<OrderItemRequest> items) {
}
