package com.zhe.mall.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 {@code SET NX PX} + Lua 释放的分布式锁（见需求文档 19.8 / 27.2 ADR-014）。
 *
 * <p>刻意不引入 Redisson：本项目锁的临界区只有"缓存重建"这类毫秒级操作，自研实现依赖面更小、行为可解释。
 * 代价是没有看门狗与可重入，因此：<b>锁 TTL 必须大于临界区最坏耗时，且临界区内禁止调用下游服务</b>。
 *
 * <p>锁只用于减少无效并发；正确性始终由 DB 条件更新与唯一索引保证（19.8）。
 */
public class DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedLock.class);

    /** 释放锁：只有持有者（value 相等）才能删除，避免误删他人锁或过期后删掉新锁 */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate redis;

    /** 实例标识，便于排查"谁持有锁" */
    private final String nodeId;

    public DistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
        this.nodeId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public boolean available() {
        return redis != null;
    }

    /**
     * 尝试加锁，最多等待 {@code waitMillis}（每 {@code retryIntervalMillis} 重试一次）。
     *
     * @return 持有者令牌；null 表示未抢到锁（调用方应降级为直查 DB，而不是排队等待）
     */
    public String tryLock(String key, long ttlMillis, long waitMillis, long retryIntervalMillis) {
        if (redis == null) {
            return null;
        }
        String token = nodeId + ":" + Thread.currentThread().threadId() + ":" + UUID.randomUUID();
        long deadline = System.currentTimeMillis() + Math.max(0L, waitMillis);
        while (true) {
            try {
                Boolean acquired = redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(ttlMillis));
                if (Boolean.TRUE.equals(acquired)) {
                    return token;
                }
            } catch (RuntimeException e) {
                log.warn("获取分布式锁失败（降级为直查 DB）: key={}, error={}", key, e.getMessage());
                return null;
            }
            if (System.currentTimeMillis() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(Math.max(1L, retryIntervalMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /** 释放锁；token 不匹配或 key 已过期时返回 false，两者都属正常情况。 */
    public boolean unlock(String key, String token) {
        if (redis == null || token == null) {
            return false;
        }
        try {
            Long released = redis.execute(new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    Collections.singletonList(key), token);
            return released != null && released > 0;
        } catch (RuntimeException e) {
            log.warn("释放分布式锁失败（依赖 TTL 自然过期）: key={}, error={}", key, e.getMessage());
            return false;
        }
    }
}