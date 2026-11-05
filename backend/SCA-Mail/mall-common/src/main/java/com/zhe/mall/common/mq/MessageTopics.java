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

    /**
     * 订单已支付（商品侧销量回写，G-12）。
     *
     * <p>与 {@link #PAY_SUCCESS} 分开而不是复用：支付事件由 mall-pay 生产、只带 paymentNo 级别的字段，
     * 而销量回写必须依赖<b>订单明细</b>（product_id + qty），订单明细只在 mall-order 的库里。
     * 若让商品服务回查订单，就会把"消费事件"变成同步依赖；因此由订单侧在支付状态 CAS 成功后
     * 发布本事件，商品侧只需按明细累加。
     */
    public static final String ORDER_PAID = "TOPIC_ORDER_PAID";

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

    /** 订单已支付（销量回写），与本文件 {@link #TAG_PAID} 区分：tag 名带 ORDER 前缀避免与支付事件混淆。 */
    public static final String TAG_ORDER_PAID = "ORDER_PAID";

    public static final String TAG_CANCELLED = "CANCELLED";

    public static final String TAG_ROLLBACK = "ROLLBACK";

    public static final String TAG_SECKILL = "SECKILL";

    private MessageTopics() {
    }
}
