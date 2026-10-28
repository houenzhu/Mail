package com.zhe.mall.pay.dto;

/** 对账差异条目（28.8）。 */
public record ReconcileDiffVO(String paymentNo, String channelTradeNo, String diffType, String detail) {
}
