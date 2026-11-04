package com.zhe.mall.api.dto;

import jakarta.validation.constraints.Size;

/**
 * 后台驳回退款请求（12.24）。
 *
 * @param reason 驳回原因，展示给买家并从 {@code refund.fail_reason} 落库。
 *               允许为空（后台一键驳回），服务端补默认文案，但长度与列宽一致。
 */
public record AdminRefundRejectRequest(
        @Size(max = 128, message = "驳回原因长度不能超过 128") String reason) {
}
