package com.zhe.mall.api.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * 事件负载契约（需求文档 19.6「各事件 payload」）。
 *
 * <p>放在 mall-api 的理由与 Feign DTO 相同：生产方与消费方在<b>不同服务</b>，
 * 必须有同一份字段定义，否则联调时字段漂移只能靠口口相传（18.5 要求）。
 * 统一信封 {@code MessageEnvelope} 在 mall-common，业务负载在本包。
 *
 * <p>兼容口径（19.6）：<b>只增字段不改语义</b>；新增字段必须可空或带默认值。
 * 因此这里全部用 record（不可变、天然线程安全），字段以 JSON 名对齐。
 */
public final class MessageEvents {

    private MessageEvents() {
    }

    /** 订单明细行（19.6 的 {@code items[{skuId, quantity, price}]}）。 */
    public record Item(Long skuId, Integer quantity, BigDecimal price) {
    }

    /**
     * {@code TOPIC_ORDER_CREATED}（tag=CREATED，key=orderNo）。
     *
     * <p>消费方：promotion（券核销后续）、inventory（异步后续动作）。
     */
    public record OrderCreatedEvent(
            String orderNo,
            Long userId,
            BigDecimal totalAmount,
            BigDecimal payAmount,
            List<Item> items,
            Long couponId) {
    }

    /**
     * {@code TOPIC_ORDER_TIMEOUT}（tag=TIMEOUT，key=orderNo）。
     *
     * <p>生产时带延时级别 16（30 分钟）；消费方 order 侧<b>必须再校验状态仍为 10</b>，
     * 因为用户可能已经支付或取消（19.6）。
     */
    public record OrderTimeoutEvent(
            String orderNo,
            Long userId,
            String expireAt) {
    }

    /**
     * {@code TOPIC_PAY_SUCCESS}（tag=PAID，key=orderNo，幂等键 paymentNo）。
     *
     * <p>消费方：order（10 → 20）、inventory。
     * 本事件替换了支付侧原先对订单的同步 Feign 通知 —— 通知失败不再只留一行 ERROR 日志，
     * 而是由 MQ 重投与 Outbox 兜底，闭合「支付成功但订单停在待支付」的缺口（28.9）。
     */
    public record PaySuccessEvent(
            String orderNo,
            String paymentNo,
            BigDecimal payAmount,
            Integer payType,
            String paidAt,
            String channelTradeNo) {
    }

    /**
     * {@code TOPIC_ORDER_CANCELLED}（tag=CANCELLED，key=orderNo）。
     *
     * <p>消费方：promotion（释放券）、inventory（释放库存）。
     */
    public record OrderCancelledEvent(
            String orderNo,
            Long userId,
            Long couponId,
            List<Item> items,
            String reason) {
    }

    /**
     * {@code TOPIC_STOCK_ROLLBACK}（tag=ROLLBACK，key=orderNo，幂等键 orderNo+type）。
     *
     * <p>消费方：inventory。幂等最终由 {@code inventory_record} 的
     * {@code uk_order_sku_type(order_no, sku_id, type)} 保证，因此重复投递安全。
     */
    public record StockRollbackEvent(
            String orderNo,
            Long userId,
            List<Item> items,
            String reason) {
    }

    /**
     * {@code TOPIC_SEC_KILL_ORDER}（tag=SECKILL，key=activityId:userId）。
     *
     * <p>第 8 项秒杀用；此处先定义契约，避免届时生产/消费两侧各写一份。
     */
    public record SeckillOrderEvent(
            Long activityId,
            Long userId,
            Long skuId,
            Integer quantity,
            String orderNo) {
    }
}
