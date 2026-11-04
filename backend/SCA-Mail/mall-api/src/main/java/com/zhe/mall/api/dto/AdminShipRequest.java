package com.zhe.mall.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台发货请求（UC-ORDER-07）。
 *
 * <p>物流公司与单号都是必填：没有单号的"发货"无法让买家追踪，运营也说不清货去哪了。
 * 长度上限与 {@code orders.shipping_company / tracking_no} 的列宽一致。
 */
public record AdminShipRequest(
        @NotBlank @Size(max = 64) String shippingCompany,
        @NotBlank @Size(max = 64) String trackingNo) {
}
