package com.zhe.mall.pay.support;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.pay.channel.PayChannel;
import com.zhe.mall.pay.config.PayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道注册表：按 {@code mall.pay.channel.code} 取当前通道（ADR-019）。
 *
 * <p>用"配置选通道"而不是"代码里 if (MOCK)"，第四阶段接微信/支付宝时只需新增一个 {@link PayChannel} 实现。
 */
@Component
public class PayChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(PayChannelRegistry.class);

    private final Map<String, PayChannel> channels = new LinkedHashMap<>();

    private final String currentCode;

    public PayChannelRegistry(List<PayChannel> payChannels, PayProperties properties) {
        payChannels.forEach(channel -> channels.put(channel.code(), channel));
        this.currentCode = properties.getChannel().getCode();
        log.info("支付通道注册完成: 已实现={}, 当前={}", channels.keySet(), currentCode);
    }

    /** 当前通道；未实现或未配置时按 35007 处理，避免"静默用错通道"。 */
    public PayChannel current() {
        PayChannel channel = channels.get(currentCode);
        if (channel == null) {
            log.error("支付通道未实现: configured={}, available={}", currentCode, channels.keySet());
            throw new BusinessException(ResultCode.PAY_CHANNEL_UNAVAILABLE, "支付渠道不可用: " + currentCode);
        }
        return channel;
    }

    /**
     * 按回调路径上的渠道码取通道（{@code /api/pay/callback/{channel}}）。
     *
     * <p>不认识的渠道码返回 35007：宁可拒绝，也不能拿当前通道的密钥去验另一个渠道的签名。
     */
    public PayChannel byCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ResultCode.PAY_CHANNEL_UNAVAILABLE, "渠道码不能为空");
        }
        PayChannel channel = channels.get(code.trim().toUpperCase());
        if (channel == null) {
            throw new BusinessException(ResultCode.PAY_CHANNEL_UNAVAILABLE, "未知支付渠道: " + code);
        }
        return channel;
    }
}
