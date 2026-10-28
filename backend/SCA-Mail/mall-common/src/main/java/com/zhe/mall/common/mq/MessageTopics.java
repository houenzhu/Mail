package com.zhe.mall.common.mq;

/**
 * Topic 常量（需求文档 19.6）。
 *
 * <p>Topic 名是**跨服务契约**：生产方与消费方必须写字面量一致，因此集中定义。
 * 改名等于破坏契约，需要按 19.6 的兼容口径（先停消费端再停产出端）走。
 */
public final class MessageTopics {

    /** 下单成功后的异步后续动作（promotion、inventory 消费）。 */
    public static final String ORDER_CREATED = "TOPIC_ORDER_CREATED";

    /** 支付超时关单；生产时带 delayLevel=16（30 分钟），消费时须再校验状态仍为待支付。 */
    public static final String ORDER_TIMEOUT = "TOPIC_ORDER_TIMEOUT";

    /** 支付成功；驱动订单状态流转（本项用于替换支付侧对订单的同步 Feign 通知）。 */
    public static final String PAY_SUCCESS = "TOPIC_PAY_SUCCESS";

    /** 订单取消/关单；释放券与库存。 */
    public static final String ORDER_CANCELLED = "TOPIC_ORDER_CANCELLED";

    /** 库存回滚；幂等靠 inventory_record 唯一键。 */
    public static final String STOCK_ROLLBACK = "TOPIC_STOCK_ROLLBACK";

    /** 秒杀异步下单（第 8 项）。 */
    public static final String SEC_KILL_ORDER = "TOPIC_SEC_KILL_ORDER";

    // ---------------- Tag（19.6 表） ----------------

    public static final String TAG_CREATED = "CREATED";

    public static final String TAG_TIMEOUT = "TIMEOUT";

    public static final String TAG_PAID = "PAID";

    public static final String TAG_CANCELLED = "CANCELLED";

    public static final String TAG_ROLLBACK = "ROLLBACK";

    public static final String TAG_SECKILL = "SECKILL";

    private MessageTopics() {
    }
}
