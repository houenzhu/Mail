package com.zhe.mall.pay.channel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 支付通道抽象（ADR-019）。
 *
 * <p>本阶段只实现 {@link MockPayChannel}：要验证的是「支付单 → 验签 → 幂等 → 状态联动 → 对账」这条链路，
 * 而不是渠道 SDK 集成。接口在这里定死，第四阶段换真实通道时业务代码零改动。
 *
 * <p>约定：所有方法都<b>不抛业务异常</b>，用返回值表达结果（渠道侧的"失败"是正常业务流，不是异常）。
 */
public interface PayChannel {

    /** 渠道码，落库为 {@code payment.channel}。 */
    String code();

    /** 签名算法标识，随渠道参数返回给客户端（28.4）。 */
    default String signType() {
        return "HMAC-SHA256";
    }

    /** 渠道下单：返回预支付标识与渠道流水号。 */
    ChannelPayResult unifiedOrder(String paymentNo, BigDecimal amount, Integer payType);

    /** 主动查询渠道交易状态（ADR-023 回调丢失兜底）。 */
    ChannelQueryResult query(String paymentNo, String channelTradeNo, BigDecimal amount);

    /** 渠道退款（Mock 通道同步返回）。 */
    ChannelRefundResult refund(String paymentNo, String refundNo, BigDecimal amount);

    /**
     * 支付确认钩子：本地确认支付成功后回调渠道侧（Mock 通道用它维护"渠道侧账"）。
     *
     * <p>真实通道由渠道自己维护账单，这里保持空实现即可——把它放进接口是为了让对账用例
     * 能在不接真通道的前提下拿到"双侧可比的账"（28.8）。
     */
    default void confirmPaid(String paymentNo, String channelTradeNo, java.math.BigDecimal amount,
                             java.time.LocalDate bizDate) {
    }

    /** 渠道账单 / 流水接口（替代真实通道的账单文件解析，28.8）。 */    List<ChannelBillItem> bill(LocalDate bizDate);
}
