package com.zhe.mall.common.mq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 生产者薄封装（19.6）。
 *
 * <p>只暴露「发一条」这一个动作：Topic / Tag / Key / 延时级别都由 Outbox 记录驱动，
 * 业务代码不直接接触 RocketMQ API，后续换实现（或加事务消息）不影响业务。
 */
public class RocketMqProducer {

    private static final Logger log = LoggerFactory.getLogger(RocketMqProducer.class);

    private final DefaultMQProducer producer;

    private final String nameServer;

    public RocketMqProducer(String nameServer, String producerGroup,
                            int sendTimeoutMillis, int retryTimesWhenSendFailed) {
        this.nameServer = nameServer;
        this.producer = new DefaultMQProducer(producerGroup);
        this.producer.setNamesrvAddr(nameServer);
        this.producer.setSendMsgTimeout(sendTimeoutMillis);
        this.producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
        this.producer.setVipChannelEnabled(false);
        // 每次启动用不同实例名，避免与同名实例的 broker 连接复用导致 offset 串味
        this.producer.setInstanceName(producerGroup + "-" + System.nanoTime());
    }

    /** 启动；失败直接抛出，让服务启动阶段就暴露配置问题。 */
    public void start() throws Exception {
        producer.start();
        log.info("RocketMQ 生产者已启动: group={}, nameServer={}", producer.getProducerGroup(), nameServer);
    }

    public void shutdown() {
        try {
            producer.shutdown();
        } catch (Exception e) {
            log.warn("关闭 RocketMQ 生产者失败: {}", e.getMessage());
        }
    }

    /**
     * 发送一条消息。
     *
     * @param delayLevel RocketMQ 固定延时级别，0 表示立即
     * @return true 表示 broker 确认接收（SEND_OK）
     */
    public boolean send(String topic, String tag, String key, String body, int delayLevel) throws Exception {
        Message message = new Message(topic, tag, key, body.getBytes(StandardCharsets.UTF_8));
        if (delayLevel > 0) {
            message.setDelayTimeLevel(delayLevel);
        }
        SendResult result = producer.send(message);
        boolean ok = result != null && SendStatus.SEND_OK.equals(result.getSendStatus());
        if (!ok) {
            log.warn("消息发送未确认: topic={}, key={}, result={}", topic, key, result);
        }
        return ok;
    }
}
