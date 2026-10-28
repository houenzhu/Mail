package com.zhe.mall.common.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 缓存组件装配（见需求文档 27.3）。
 *
 * <p>只有 classpath 上存在 {@code spring-data-redis} 时才生效：{@code mall-common} 对
 * {@code spring-boot-starter-data-redis} 的依赖是 {@code optional}，未接入 Redis 的服务（mall-user /
 * mall-inventory 等）不会被动引入 Redis 连接与健康检查。
 *
 * <p>这里用 {@link ObjectProvider} 而不是 {@code @ConditionalOnBean} 获取模板：autoconfiguration 之间的
 * 相对顺序不保证，{@code @ConditionalOnBean} 可能因评估时机而过早失效（26.8 类问题）。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(CacheProperties.class)
public class MallCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(ObjectProvider<StringRedisTemplate> redisProvider) {
        return new DistributedLock(redisProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentGuard idempotentGuard(ObjectProvider<StringRedisTemplate> redisProvider) {
        return new IdempotentGuard(redisProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public MallCache mallCache(ObjectProvider<StringRedisTemplate> redisProvider,
                               DistributedLock distributedLock,
                               CacheProperties cacheProperties) {
        return new MallCache(redisProvider.getIfAvailable(), distributedLock, cacheProperties);
    }
}