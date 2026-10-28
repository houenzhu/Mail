package com.zhe.mall.pay.channel;

import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.pay.enums.ChannelTradeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock 支付通道（ADR-019）：不接真实渠道，但**行为口径按真实渠道设计**。
 *
 * <p>设计要点：
 * <ol>
 *   <li>下单 / 查询 / 退款全部<b>确定性</b>返回，不给 E2E 留"随机失败"的坑；</li>
 *   <li>"渠道侧账"用 Redis hash 扮演（{@link MallCacheKeys#payMockBill}）：下单登记 PENDING、
 *       支付成功改 SUCCESS，使对账有真实的双侧数据可比；</li>
 *   <li>Redis 写入全部 <b>fail-soft</b>：渠道记账失败不能反过来阻断建单（28.11 #18）；
 *       对账时账单为空会退化成全量 LOCAL_ONLY，属可解释的降级。</li>
 * </ol>
 *
 * <p><b>与真实渠道的差距</b>（28.12 已登记）：没有异步性，不会出现"回调先于同步返回"、"渠道重复投递风暴"、
 * "账单文件与流水接口口径不一致"等真实时序问题，第四阶段接真通道时必须重跑 28.11 全量用例。
 */
@Component
public class MockPayChannel implements PayChannel {

    private static final Logger log = LoggerFactory.getLogger(MockPayChannel.class);

    private static final String PREPAY_PREFIX = "MOCK_PREPAY_";

    private static final String TRADE_PREFIX = "MOCK_TRADE_";

    private static final String REFUND_PREFIX = "MOCK_REFUND_";

    private static final String FIELD_SEPARATOR = "|";

    private static final int VALUE_PARTS = 3;

    private final StringRedisTemplate redis;

    public MockPayChannel(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    @Override
    public String code() {
        return "MOCK";
    }

    @Override
    public ChannelPayResult unifiedOrder(String paymentNo, BigDecimal amount, Integer payType) {
        String prepayId = PREPAY_PREFIX + paymentNo;
        String channelTradeNo = TRADE_PREFIX + paymentNo;
        registerBill(paymentNo, channelTradeNo, amount, ChannelTradeStatus.PENDING);
        return new ChannelPayResult(prepayId, channelTradeNo);
    }

    /**
     * 查询渠道状态：Mock 通道恒定返回成功。
     *
     * <p>这是"回调丢失兜底"用例（28.11 #11）能成立的前提——不投递回调时，同步接口仍能把支付单对齐到成功。
     */
    @Override
    public ChannelQueryResult query(String paymentNo, String channelTradeNo, BigDecimal amount) {
        String tradeNo = channelTradeNo == null || channelTradeNo.isBlank()
                ? TRADE_PREFIX + paymentNo : channelTradeNo;
        return ChannelQueryResult.of(ChannelTradeStatus.SUCCESS, tradeNo, amount, null);
    }

    @Override
    public ChannelRefundResult refund(String paymentNo, String refundNo, BigDecimal amount) {
        return ChannelRefundResult.ok(REFUND_PREFIX + refundNo);
    }

    /**
     * 渠道流水：读取本机 Redis 中的"渠道侧账"。
     *
     * <p>Redis 不可用时返回空列表并告警，由对账结果体现差异，而不是抛异常让任务整个失败。
     */
    @Override
    public List<ChannelBillItem> bill(LocalDate bizDate) {
        if (redis == null) {
            log.warn("Mock 通道账单不可用（Redis 未接入）: bizDate={}", bizDate);
            return List.of();
        }
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(MallCacheKeys.payMockBill(bizDate.toString()));
            List<ChannelBillItem> items = new ArrayList<>(entries.size());
            entries.forEach((field, value) -> {
                ChannelBillItem item = parseBillItem(String.valueOf(field), String.valueOf(value));
                if (item != null) {
                    items.add(item);
                }
            });
            return items;
        } catch (RuntimeException e) {
            log.warn("Mock 通道账单读取失败（本次对账按空账单处理）: bizDate={}, error={}", bizDate, e.getMessage());
            return List.of();
        }
    }

    /** 支付成功时把渠道侧账改成 SUCCESS（真实渠道由渠道自己维护，Mock 由我们代劳）。 */
    @Override
    public void confirmPaid(String paymentNo, String channelTradeNo, BigDecimal amount, LocalDate bizDate) {
        registerBill(paymentNo, channelTradeNo, amount, ChannelTradeStatus.SUCCESS, bizDate);
    }

    private void registerBill(String paymentNo, String channelTradeNo, BigDecimal amount,
                              ChannelTradeStatus status) {
        registerBill(paymentNo, channelTradeNo, amount, status, LocalDate.now());
    }

    private void registerBill(String paymentNo, String channelTradeNo, BigDecimal amount,
                              ChannelTradeStatus status, LocalDate bizDate) {
        if (redis == null || channelTradeNo == null || channelTradeNo.isBlank()) {
            return;
        }
        String value = paymentNo + FIELD_SEPARATOR
                + (amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                + FIELD_SEPARATOR + status.name();
        try {
            redis.opsForHash().put(MallCacheKeys.payMockBill(bizDate.toString()), channelTradeNo, value);
        } catch (RuntimeException e) {
            log.warn("Mock 通道记账失败（不影响建单，仅影响对账）: paymentNo={}, error={}",
                    paymentNo, e.getMessage());
        }
    }

    private ChannelBillItem parseBillItem(String channelTradeNo, String value) {
        String[] parts = value == null ? new String[0] : value.split("\\" + FIELD_SEPARATOR);
        if (parts.length < VALUE_PARTS) {
            log.warn("Mock 通道账单条目格式非法，已跳过: channelTradeNo={}, value={}", channelTradeNo, value);
            return null;
        }
        try {
            return new ChannelBillItem(channelTradeNo, parts[0], new BigDecimal(parts[1]),
                    ChannelTradeStatus.valueOf(parts[2]));
        } catch (RuntimeException e) {
            log.warn("Mock 通道账单条目解析失败，已跳过: channelTradeNo={}, value={}", channelTradeNo, value);
            return null;
        }
    }
}
