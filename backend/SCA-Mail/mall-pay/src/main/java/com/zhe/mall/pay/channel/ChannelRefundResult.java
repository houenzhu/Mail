package com.zhe.mall.pay.channel;

/** 渠道退款结果。 */
public record ChannelRefundResult(boolean success, String channelRefundNo, String failReason) {

    public static ChannelRefundResult ok(String channelRefundNo) {
        return new ChannelRefundResult(true, channelRefundNo, null);
    }

    public static ChannelRefundResult fail(String failReason) {
        return new ChannelRefundResult(false, null, failReason);
    }
}
