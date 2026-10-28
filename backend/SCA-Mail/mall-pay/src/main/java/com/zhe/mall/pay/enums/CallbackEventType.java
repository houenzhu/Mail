package com.zhe.mall.pay.enums;

/**
 * 回调流水事件类型，落库为 {@code payment_callback_log.event_type}。
 *
 * <p>它是防重放唯一键 {@code uk_event(channel, channel_trade_no, event_type)} 的一半，
 * 把"支付回调"与"退款回调"分开，避免渠道复用同一个流水号时被误判为重复。
 */
public enum CallbackEventType {

    PAY_CALLBACK,

    REFUND_CALLBACK,

    QUERY
}
