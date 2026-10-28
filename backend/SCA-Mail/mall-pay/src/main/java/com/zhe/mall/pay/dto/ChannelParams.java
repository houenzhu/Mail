package com.zhe.mall.pay.dto;

/** 渠道支付参数：客户端拿它去调渠道 SDK（Mock 通道用于校验签名串口径）。 */
public record ChannelParams(String prepayId, String nonceStr, String sign, String signType) {
}
