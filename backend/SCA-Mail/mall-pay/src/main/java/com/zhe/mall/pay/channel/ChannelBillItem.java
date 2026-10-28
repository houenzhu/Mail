package com.zhe.mall.pay.channel;

import com.zhe.mall.pay.enums.ChannelTradeStatus;

import java.math.BigDecimal;

/** 渠道账单条目（对账数据源，28.8）。 */
public record ChannelBillItem(String channelTradeNo,
                              String paymentNo,
                              BigDecimal amount,
                              ChannelTradeStatus status) {
}
