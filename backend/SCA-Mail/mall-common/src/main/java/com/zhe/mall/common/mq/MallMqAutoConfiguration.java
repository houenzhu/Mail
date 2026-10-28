package com.zhe.mall.common.mq;

import com.zhe.mall.common.cache.DistributedLock;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * RocketMQ 自动装配（19.6 / 20.3）。
 *
 * <p>与 {@code MallCacheAutoConfiguration} 同一套路：{@code @ConditionalOnClass} 判断服务
 * 有没有真的把 rocketmq-client（optional 依赖）加进来，{@code @ConditionalOnMissingBean}
 * 允许业务覆盖，{@code ObjectProvider} 规避自动配置相对顺序不确定的问题。
 *
 * <p><b>按需装配</b>：生产端与消费端是两件独立的事，不绑在一起 ——
 * 否则「只消费不生产」的 mall-inventory 也会白起一个 producer。
 * <ul>
 *   <li>生产者 / Outbox / 中继：靠 {@code mall.mq.producer-enabled}（默认开），
 *       只消费的服务置 false；</li>
 *   <li>消费者注册器：靠 {@code mall.mq.consumer.enabled=true} 显式开启，
 *       即「本服务要不要收消息」。</li>
 * </ul>
 *
 * <p><b>为什么不用 {@code @ConditionalOnBean(LocalMessageMapper)}</b>：Mapper 是业务服务用
 * {@code @MapperScan} 在配置类解析阶段注册的，而 {@code @ConditionalOnBean} 在自动配置里的
 * 评估时机不可靠 —— 实测被误判为"不存在"，导致 OutboxService 缺失、服务启动失败。
 * 因此改用显式属性开关，把"要不要生产消息"变成配置问题而不是条件推断问题。
 */
@AutoConfiguration
@ConditionalOnClass(DefaultMQProducer.class)
@ConditionalOnProperty(name = "mall.mq.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MallMqProperties.class)
public class MallMqAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MallMqAutoConfiguration.class);

    private static final String DEFAULT_SERVICE_NAME = "mall-service";

    /**
     * 生产者。
     *
     * <p>用显式的 {@code mall.mq.producer-enabled} 开关（默认开）而不是
     * {@code @ConditionalOnBean(LocalMessageMapper)}：Mapper 是业务服务用
     * {@code @MapperScan} 在配置类解析阶段注册的，{@code @ConditionalOnBean} 在自动配置里
     * 评估时机不可靠 —— 实测会误判为"不存在"，导致 OutboxService 缺失、服务启动失败。
     * 只消费的服务（mall-inventory）把该开关置 false 即可。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "mall.mq.producer-enabled", havingValue = "true", matchIfMissing = true)
    public RocketMqProducer rocketMqProducer(MallMqProperties properties, Environment environment) throws Exception {
        String group = properties.getProducerGroupPrefix() + serviceName(environment);
        RocketMqProducer producer = new RocketMqProducer(
                properties.getNameServer(),
                group,
                properties.getSendTimeoutMillis(),
                properties.getRetryTimesWhenSendFailed());
        producer.start();
        return producer;
    }

    /** Outbox 写入器：与生产者同开关（有生产者才需要写 Outbox）。 */
    @Bean
    @ConditionalOnProperty(name = "mall.mq.producer-enabled", havingValue = "true", matchIfMissing = true)
    public OutboxService outboxService(LocalMessageMapper mapper, Environment environment) {
        log.info("Outbox 写入器已装配: table=local_message, service={}", serviceName(environment));
        return new OutboxService(mapper, environment);
    }

    /** Outbox 中继：有写入器与生产者就启动补发。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "mall.mq.producer-enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelayTask outboxRelayTask(LocalMessageMapper mapper,
                                          RocketMqProducer producer,
                                          MallMqProperties properties,
                                          ObjectProvider<DistributedLock> lockProvider) {
        OutboxRelayTask task = new OutboxRelayTask(mapper, producer, properties, lockProvider);
        task.start();
        return task;
    }

    /** 消费者注册器：必须显式 {@code mall.mq.consumer.enabled=true} 才装配。 */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "mall.mq.consumer.enabled", havingValue = "true")
    public RocketMqConsumerRegistrar rocketMqConsumerRegistrar(MallMqProperties properties,
                                                             ConsumedMessageMapper mapper,
                                                             Environment environment) {
        log.info("RocketMQ 消费者注册器已装配: service={}, nameServer={}",
                serviceName(environment), properties.getNameServer());
        return new RocketMqConsumerRegistrar(
                properties.getNameServer(), serviceName(environment), properties, mapper);
    }

    /**
     * 应用就绪后统一启动消费者。
     *
     * <p>订阅是在各服务的 {@code @Configuration} 构造期登记的，那时 handler 依赖的 Bean
     * 可能还没就绪；推迟到 {@code ApplicationReadyEvent} 才 start，避免"消费者先于业务 Bean 工作"。
     */
    @Bean
    @ConditionalOnProperty(name = "mall.mq.consumer.enabled", havingValue = "true")
    public ApplicationListener<ApplicationReadyEvent> mallMqConsumerBootstrap(RocketMqConsumerRegistrar registrar) {
        return event -> registrar.startAll();
    }

    private static String serviceName(Environment environment) {
        String name = environment.getProperty("spring.application.name");
        return name == null || name.isBlank() ? DEFAULT_SERVICE_NAME : name;
    }
}
