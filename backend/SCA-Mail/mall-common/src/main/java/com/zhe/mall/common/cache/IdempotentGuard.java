package com.zhe.mall.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 幂等标记（见需求文档 19.9 / 27.7）。
 *
 * <p>语义：{@code SET NX} 成功 = 首次请求；失败则读取已存在的值——{@link MallCacheKeys#PENDING_VALUE}
 * 表示"前一个请求还在处理"，业务值（如 orderNo）表示"已经有结果，直接返回"。
 *
 * <p>Redis 标记只是**拦截层**：标记丢失（淘汰 / Redis 不可用）不影响正确性，DB 唯一索引仍是最终权威。
 */
public class IdempotentGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotentGuard.class);

    private final StringRedisTemplate redis;

    public IdempotentGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean available() {
        return redis != null;
    }

    /** 抢占标记；返回 false 表示已存在（首次抢占失败或 Redis 不可用）。 */
    public boolean claim(String key, String value, Duration ttl) {
        if (redis == null) {
            return false;
        }
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, value, ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn("写入幂等标记失败（降级为 DB 唯一索引兜底）: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /** 读取标记当前值；不存在或 Redis 异常返回 null。 */
    public String current(String key) {
        if (redis == null) {
            return null;
        }
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("读取幂等标记失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 回填业务结果：值换成业务键（如 orderNo），TTL 换成**业务幂等窗口**。
     *
     * <p>占位值与结果值分两个 TTL 是有意的：占位（PENDING）只需要覆盖"一次请求的处理时长"，
     * 成功回填后才需要覆盖整个幂等窗口（下单 24h）。
     */
    public void complete(String key, String value, Duration ttl) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (RuntimeException e) {
            log.warn("回填幂等标记失败: key={}, error={}", key, e.getMessage());
        }
    }

    /** 释放标记（业务失败且允许重试时使用）。 */
    public void release(String key) {
        if (redis == null) {
            return;
        }
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("释放幂等标记失败: key={}, error={}", key, e.getMessage());
        }
    }
}