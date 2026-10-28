package com.zhe.mall.pay.enums;

/**
 * 渠道侧交易状态（{@code PayChannel} 的返回口径），与本地状态解耦。
 *
 * <p>刻意不复用 {@link PaymentStatus}：渠道状态是外部输入，本地状态是内部事实，
 * 两者混用会让"未知状态当成功"这类缺陷难以发现（28.6 纪律 2）。
 */
public enum ChannelTradeStatus {

    /** 渠道侧还没支付结果（如用户未付款） */
    PENDING,

    /** 渠道侧支付成功 */
    SUCCESS,

    /** 渠道侧支付失败 */
    FAILED,

    /** 渠道侧查无此单 */
    NOT_FOUND;

    public static ChannelTradeStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return switch (raw.trim().toUpperCase()) {
            case "SUCCESS" -> SUCCESS;
            case "FAIL", "FAILED" -> FAILED;
            case "NOT_FOUND" -> NOT_FOUND;
            default -> PENDING;
        };
    }
}
