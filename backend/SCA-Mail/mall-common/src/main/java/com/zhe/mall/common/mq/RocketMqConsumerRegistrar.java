package com.zhe.mall.common.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RocketMQ 消费者注册器（19.6）。
 *
 * <p>把三类横切关注点收在一处，业务侧只写 {@link MessageHandler}：
 * <ol>
 *   <li><b>幂等</b>：先往 {@code consumed_message} 插 msg_id 认领，成功才执行业务；
 *       重复投递撞主键 → 直接 acknowledge（不重复处理）；业务失败 → 删除认领行并重投；</li>
 *   <li><b>traceId 恢复</b>：把信封里的 traceId 重新放进 MDC，消费链路日志可与生产端串起来；</li>
 *   <li><b>顺序无关</b>：并发消费 + 业务侧用自己的状态机/CAS 兜底（19.6「顺序」靠分区键，
 *       同一 key 进同一队列，因此同一业务对象仍是顺序的）。</li>
 * </ol>
 *
 * <p>每个订阅（topic + tag）一个 push consumer、一个消费组。组名带业务后缀，
 * 便于用 {@code rocketmq_consumer_progress} 单独观察每个消费者的堆积。
 */
public class RocketMqConsumerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(RocketMqConsumerRegistrar.class);

    private final List<DefaultMQPushConsumer> consumers = new CopyOnWriteArrayList<>();

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private boolean started;

    private final String nameServer;

    private final String serviceName;

    private final MallMqProperties properties;

    private final ConsumedMessageMapper consumedMessageMapper;

    public RocketMqConsumerRegistrar(String nameServer,
                                     String serviceName,
                                     MallMqProperties properties,
                                     ConsumedMessageMapper consumedMessageMapper) {
        this.nameServer = nameServer;
        this.serviceName = serviceName;
        this.properties = properties;
        this.consumedMessageMapper = consumedMessageMapper;
    }

    public String serviceName() {
        return serviceName;
    }

    /**
     * 订阅一个 Topic/Tag（注册即生效，但真正 start 推迟到 {@link #startAll()}）。
     *
     * <p>不在这里直接 start 的原因：消费者会立即回拉消息并回调业务 handler，
     * 而本方法通常在业务服务的 {@code @Configuration} 构造期被调用 ——
     * 那时上下文还没 refresh 完，handler 依赖的 Bean 可能尚未就绪。
     * 因此先「登记」，由 {@code ApplicationReadyEvent} 统一启动。
     *
     * @param business 业务短名（如 {@code order-timeout}），用于拼消费组与消费方标识
     */
    public void subscribe(String business, String topic, String tag, MessageHandler handler) {
        subscriptions.add(new Subscription(business, topic, tag, handler));
        log.info("已登记 RocketMQ 订阅（待应用就绪后启动）: business={}, topic={}, tag={}", business, topic, tag);
    }

    private record Subscription(String business, String topic, String tag, MessageHandler handler) {
    }

    /** 由 {@code ApplicationReadyEvent} 触发：启动全部已登记的订阅。 */
    public synchronized void startAll() {
        if (!started) {
            started = true;
        } else {
            return;
        }
        for (Subscription subscription : subscriptions) {
            try {
                startOne(subscription);
            } catch (Exception e) {
                // 单个订阅起不来不应该拖垮整个服务：记 ERROR，其余订阅继续
                log.error("启动 RocketMQ 消费者失败: topic={}, business={}",
                        subscription.topic(), subscription.business(), e);
            }
        }
    }

    private void startOne(Subscription subscription) throws Exception {
        MallMqProperties.Consumer consumerProps = properties.getConsumer();
        String group = properties.getConsumerGroupPrefix() + serviceName + "-" + subscription.business();

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(consumerProps.getConsumeThreadMin());
        consumer.setConsumeThreadMax(consumerProps.getConsumeThreadMax());
        consumer.setConsumeMessageBatchMaxSize(consumerProps.getConsumeMessageBatchMaxSize());
        consumer.setMaxReconsumeTimes(consumerProps.getMaxReconsumeTimes());
        consumer.setInstanceName(group + "-" + System.nanoTime());
        String tag = subscription.tag();
        consumer.subscribe(subscription.topic(), tag == null || tag.isBlank() ? "*" : tag);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
                handleBatch(subscription.topic(), subscription.business(), messages, subscription.handler()));

        consumer.start();
        consumers.add(consumer);
        log.info("RocketMQ 消费者已启动: group={}, topic={}, tag={}", group, subscription.topic(), tag);
    }

    private ConsumeConcurrentlyStatus handleBatch(String topic,
                                                  String business,
                                                  List<MessageExt> messages,
                                                  MessageHandler handler) {
        // 批量消费目前固定为 1 条（consumeMessageBatchMaxSize 默认 1），这里按批次顺序处理
        List<String> claimed = new ArrayList<>(messages.size());
        for (MessageExt raw : messages) {
            MessageEnvelope envelope;
            try {
                envelope = MessageCodec.envelopeFromJson(new String(raw.getBody(), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                // 报文都解析不了，重投也没用：acknowledge 掉，避免毒消息堵塞队列（留 ERROR 供排查）
                log.error("消息体无法解析，已丢弃: topic={}, msgId={}, error={}",
                        topic, raw.getMsgId(), e.getMessage());
                continue;
            }

            String consumerTag = serviceName + ":" + business;
            try {
                consumedMessageMapper.insertProcessing(envelope.msgId(), topic, consumerTag, envelope.key());
            } catch (DuplicateKeyException e) {
                // 重复投递：已经处理过（成功）或正在处理，直接跳过
                log.info("消息重复投递，跳过: msgId={}, topic={}, key={}",
                        envelope.msgId(), topic, envelope.key());
                continue;
            }

            String previousTraceId = MDC.get("traceId");
            try {
                if (envelope.traceId() != null) {
                    MDC.put("traceId", envelope.traceId());
                }
                if (handler.handle(envelope)) {
                    consumedMessageMapper.markSuccess(envelope.msgId());
                    claimed.add(envelope.msgId());
                } else {
                    // 处理失败：释放认领，让重投能再次进入
                    consumedMessageMapper.deleteByMsgId(envelope.msgId());
                    log.warn("消息处理返回失败，将重投: msgId={}, topic={}, key={}",
                            envelope.msgId(), topic, envelope.key());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            } catch (Exception e) {
                consumedMessageMapper.deleteByMsgId(envelope.msgId());
                log.error("消息处理异常，将重投: msgId={}, topic={}, key={}",
                        envelope.msgId(), topic, envelope.key(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            } finally {
                if (envelope.traceId() != null) {
                    if (previousTraceId == null) {
                        MDC.remove("traceId");
                    } else {
                        MDC.put("traceId", previousTraceId);
                    }
                }
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /** 应用关闭时优雅停机，避免 offset 未提交导致重复消费。 */
    public void shutdown() {
        for (DefaultMQPushConsumer consumer : consumers) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.warn("关闭 RocketMQ 消费者失败: group={}, error={}", consumer.getConsumerGroup(), e.getMessage());
            }
        }
        consumers.clear();
    }
}
