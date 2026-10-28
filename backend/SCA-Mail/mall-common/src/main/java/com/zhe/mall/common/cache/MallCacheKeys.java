package com.zhe.mall.common.cache;

/**
 * Redis key 唯一构造入口（见需求文档 19.1 / 27.3）。
 *
 * <p>禁止在业务代码里手工拼接缓存 key：key 一旦散落，「统一改前缀」「批量清理」都会变成事故。
 */
public final class MallCacheKeys {

    /** 一级前缀，用于与同实例上的其他业务隔离 */
    public static final String PREFIX = "mall:";

    /** 空值标记后缀（防穿透） */
    public static final String NULL_SUFFIX = ":null";

    /** 幂等标记的"处理中"占位值 */
    public static final String PENDING_VALUE = "PENDING";

    private static final String PRODUCT_DETAIL = PREFIX + "product:detail:";

    private static final String AUTH_REFRESH_ROTATED = PREFIX + "auth:refresh-rotated:";

    private static final String IDEM_ORDER = PREFIX + "idem:order:";

    private static final String IDEM_PAY = PREFIX + "idem:pay:";

    private static final String IDEM_PAY_CALLBACK = PREFIX + "idem:pay:callback:";

    /** Mock 通道"渠道侧流水"（仅 dev / E2E 使用，见 28.11 #16 的差异制造口径） */
    private static final String PAY_MOCK_BILL = PREFIX + "pay:mock:bill:";

    private static final String LOCK_CACHE = PREFIX + "lock:cache:";
    private static final String LOCK_TASK = PREFIX + "lock:task:";

    public static String productDetail(Long productId) {
        return PRODUCT_DETAIL + productId;
    }

    public static String productDetailNull(Long productId) {
        return PRODUCT_DETAIL + productId + NULL_SUFFIX;
    }

    /**
     * 已旋转的刷新令牌标记：存在即说明该令牌被用过（重放），值形如 {@code userId|newToken}。
     *
     * <p>Sa-Token 以会话为准，旋转后旧令牌直接失效，无法再区分"无效"与"重放"，
     * 故保留这张短 TTL 标记来维持 26.6 的重放检测与强制下线（21.8.7）。
     */
    public static String authRefreshRotated(String refreshToken) {
        return AUTH_REFRESH_ROTATED + refreshToken;
    }

    /** 下单幂等标记（19.9 / 27.7） */
    public static String orderIdempotent(Long userId, String requestId) {
        return IDEM_ORDER + userId + ":" + requestId;
    }

    /**
     * 发起支付幂等标记（28.3.5）：PENDING 占位 60s，成功后回填 paymentNo 并保留 24h。
     *
     * <p>正确性由 {@code payment.uk_order_request} 唯一索引保证，Redis 标记只负责少做无用功；
     * Redis 不可用时降级为直连 DB（28.11 #18）。
     */
    public static String payIdempotent(Long userId, String requestId) {
        return IDEM_PAY + userId + ":" + requestId;
    }

    /**
     * 渠道回调防重放标记（28.3.5 / 28.6 第 4 步）：命中即说明该事件已受理过。
     *
     * <p>最终权威是 {@code payment_callback_log.uk_event} 唯一索引——标记丢失只会让请求多走一次 DB，
     * 不会导致重复流转。
     */
    public static String payCallbackIdempotent(String channel, String channelTradeNo) {
        return IDEM_PAY_CALLBACK + channel + ":" + channelTradeNo;
    }

    /**
     * Mock 通道的渠道侧流水 hash（field = channelTradeNo，value = {@code paymentNo|amount|status}）。
     *
     * <p>Mock 通道没有真实的账单文件，用这张 hash 扮演"渠道侧账"：下单登记 PENDING、支付成功改 SUCCESS。
     * 对账用例要制造"渠道有、库中无"的差异时，直接往这张 hash 里塞一条即可，无需给生产代码开测试后门。
     */
    public static String payMockBill(String bizDate) {
        return PAY_MOCK_BILL + bizDate;
    }

    /** 缓存重建互斥锁：把 mall:xxx 映射为 mall:lock:cache:xxx */    public static String cacheRebuildLock(String cacheKey) {
        return LOCK_CACHE + stripPrefix(cacheKey);
    }

    /** 定时任务抢占锁（22.10 纪律 5） */
    public static String taskLock(String taskName) {
        return LOCK_TASK + taskName;
    }

    private static String stripPrefix(String cacheKey) {
        return cacheKey != null && cacheKey.startsWith(PREFIX) ? cacheKey.substring(PREFIX.length()) : cacheKey;
    }

    private MallCacheKeys() {
    }
}
