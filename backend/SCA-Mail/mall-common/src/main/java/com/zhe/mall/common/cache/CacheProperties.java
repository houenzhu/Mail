package com.zhe.mall.common.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 缓存与锁的可调参数（见需求文档 19.2 / 19.8 / 22.9）。
 *
 * <p>所有值都可以通过 Nacos 覆盖；关闭开关（{@code mall.cache.enabled=false}）后缓存读写退化为直连 DB。
 */
@Data
@ConfigurationProperties(prefix = "mall.cache")
public class CacheProperties {

    /** 总开关；false 时不读不写缓存（仍可正常访问 DB） */
    private boolean enabled = true;

    /** 商品详情基础 TTL（秒） */
    private long ttlSeconds = 1800L;

    /** TTL 随机抖动上限（秒），用于打散过期时间、防雪崩 */
    private long jitterSeconds = 300L;

    /** 空值标记 TTL（秒），防穿透 */
    private long nullTtlSeconds = 60L;

    /** 缓存重建锁 TTL（毫秒），必须大于回源 DB 的最坏耗时 */
    private long lockTtlMillis = 10000L;

    /** 抢锁最长等待时间（毫秒） */
    private long lockWaitMillis = 150L;

    /** 抢锁重试间隔（毫秒） */
    private long lockRetryIntervalMillis = 50L;
}