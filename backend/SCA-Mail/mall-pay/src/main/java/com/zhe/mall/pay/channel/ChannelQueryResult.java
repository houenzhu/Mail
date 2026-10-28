package com.zhe.mall.pay.channel;

import com.zhe.mall.pay.enums.ChannelTradeStatus;

import java.math.BigDecimal;

/** 渠道查询结果：{@code status} 只由渠道给出，本地据此决定流转（28.6 纪律 2）。 */
public record ChannelQueryResult(ChannelTradeStatus status,
                                 String channelTradeNo,
                                 BigDecimal amount,
                                 String failReason) {

    public static ChannelQueryResult of(ChannelTradeStatus status, String channelTradeNo,
                                        BigDecimal amount, String failReason) {
        return new ChannelQueryResult(status, channelTradeNo, amount, failReason);
    }
}
