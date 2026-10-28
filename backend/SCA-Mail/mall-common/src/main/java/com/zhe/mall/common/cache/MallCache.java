package com.zhe.mall.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Cache-Aside 读写封装（见需求文档 19.2 ~ 19.4、27.4）。
 *
 * <p>四件事一次性做对，避免每个业务各写一遍：
 * <ol>
 *   <li><b>空值标记</b>：回源结果为空时写短 TTL 标记，挡住穿透（不存在的 id 反复打 DB）；</li>
 *   <li><b>互斥重建</b>：未命中时抢锁，只有抢到锁的线程回源并回填，其余线程短暂等待后直查 DB（不排队、不阻塞）；</li>
 *   <li><b>TTL 抖动</b>：基础 TTL 叠加随机量，打散过期时间，防雪崩；</li>
 *   <li><b>fail-soft</b>：Redis 任何异常都降级为直查 DB，业务不因缓存故障失败（19.10）。</li>
 * </ol>
 *
 * <p>值统一用 JSON 字符串（ADR-016），不使用 JDK 序列化：可读、可用 RedisInsight 排查、无跨语言反序列化风险。
 */
public class MallCache {

    private static final Logger log = LoggerFactory.getLogger(MallCache.class);

    /** 空值标记：刻意不是合法 JSON，避免与真实缓存值混淆 */
    private static final String NULL_MARKER = "__NULL__";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final StringRedisTemplate redis;

    private final DistributedLock lock;

    private final CacheProperties properties;

    public MallCache(StringRedisTemplate redis, DistributedLock lock, CacheProperties properties) {
        this.redis = redis;
        this.lock = lock;
        this.properties = properties;
    }

    /** 缓存是否可用：未装配 Redis 或开关关闭时为 false，调用方直接走 loader。 */
    public boolean available() {
        return redis != null && properties.isEnabled();
    }

    /**
     * 读缓存，未命中则回源并回填。
     *
     * @param type   缓存值类型（本项目为 DTO / VO，Jackson 直接反序列化 record）
     * @param loader 回源逻辑；返回 null 表示数据不存在（会被写成空值标记）
     */
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader) {
        if (!available()) {
            return loader.get();
        }
        String cached = rawGet(key);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                return null;
            }
            T value = deserialize(key, cached, type);
            if (value != null) {
                return value;
            }
        }
        return loadUnderLock(key, type, loader);
    }

    /** 删除缓存（写路径调用；删除失败只告警，靠 TTL 自然过期兜底，见 12.20）。 */
    public void evict(String key) {
        if (!available()) {
            return;
        }
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("删除缓存失败（等待 TTL 过期兜底）: key={}, error={}", key, e.getMessage());
        }
    }

    /** 删除空值标记（数据重新存在时调用，避免 60s 内仍返回"不存在"）。 */
    public void evictNull(String key) {
        evict(key + MallCacheKeys.NULL_SUFFIX);
    }

    private <T> T loadUnderLock(String key, Class<T> type, Supplier<T> loader) {
        String lockKey = MallCacheKeys.cacheRebuildLock(key);
        String token = lock.available()
                ? lock.tryLock(lockKey, properties.getLockTtlMillis(),
                        properties.getLockWaitMillis(), properties.getLockRetryIntervalMillis())
                : null;
        if (token == null) {
            // 没抢到锁：不排队、不回填，直接回源（击穿场景下最多多打几次 DB，可接受）
            return loader.get();
        }
        try {
            String doubleChecked = rawGet(key);
            if (doubleChecked != null) {
                if (NULL_MARKER.equals(doubleChecked)) {
                    return null;
                }
                T value = deserialize(key, doubleChecked, type);
                if (value != null) {
                    return value;
                }
            }
            T loaded = loader.get();
            write(key, loaded);
            return loaded;
        } finally {
            lock.unlock(lockKey, token);
        }
    }

    private <T> void write(String key, T value) {
        try {
            if (value == null) {
                redis.opsForValue().set(key + MallCacheKeys.NULL_SUFFIX, NULL_MARKER,
                        Duration.ofSeconds(properties.getNullTtlSeconds()));
                return;
            }
            redis.delete(key + MallCacheKeys.NULL_SUFFIX);
            redis.opsForValue().set(key, MAPPER.writeValueAsString(value), Duration.ofSeconds(ttlWithJitter()));
        } catch (RuntimeException e) {
            log.warn("写入缓存失败（不影响业务）: key={}, error={}", key, e.getMessage());
        }
    }

    private long ttlWithJitter() {
        long jitter = properties.getJitterSeconds();
        long extra = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
        return properties.getTtlSeconds() + extra;
    }

    private String rawGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("读取缓存失败（降级直查 DB）: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    private <T> T deserialize(String key, String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (RuntimeException e) {
            log.warn("缓存值反序列化失败（按未命中处理并删除脏值）: key={}, error={}", key, e.getMessage());
            evict(key);
            return null;
        }
    }
}