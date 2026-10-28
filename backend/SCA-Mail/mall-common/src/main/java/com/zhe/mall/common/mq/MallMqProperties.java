package com.zhe.mall.common.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 配置（需求文档 19.6 / 22.9）。
 *
 * <p>前缀 {@code mall.mq}，与模块内的 {@code mall.cache} / {@code mall.idem} 命名口径一致。
 */
@ConfigurationProperties(prefix = "mall.mq")
public class MallMqProperties {

    /** 总开关：关闭时不装配生产者/消费者（本地调试与单测用）。 */
    private boolean enabled = true;

    /** nameserver 地址，多个用分号分隔。 */
    private String nameServer = "127.0.0.1:9876";

    /** 生产组前缀；实际 group = {@code producerGroupPrefix + 服务名}。 */
    private String producerGroupPrefix = "mall-producer-";

    /** 消费者组前缀；实际 group = {@code consumerGroupPrefix + 服务名 + "-" + 业务名}。 */
    private String consumerGroupPrefix = "mall-consumer-";

    /** 发送超时（毫秒）：写操作不重试，超时即失败并留给 Outbox 重投。 */
    private int sendTimeoutMillis = 3000;

    /** 单次发送是否重试：置 1 表示由 RocketMQ 内部重试一次（Outbox 仍是最终兜底）。 */
    private int retryTimesWhenSendFailed = 1;

    private final Outbox outbox = new Outbox();

    private final Consumer consumer = new Consumer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroupPrefix() {
        return producerGroupPrefix;
    }

    public void setProducerGroupPrefix(String producerGroupPrefix) {
        this.producerGroupPrefix = producerGroupPrefix;
    }

    public String getConsumerGroupPrefix() {
        return consumerGroupPrefix;
    }

    public void setConsumerGroupPrefix(String consumerGroupPrefix) {
        this.consumerGroupPrefix = consumerGroupPrefix;
    }

    public int getSendTimeoutMillis() {
        return sendTimeoutMillis;
    }

    public void setSendTimeoutMillis(int sendTimeoutMillis) {
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    public int getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    /** 本地消息表补发任务（20.3「本地消息表补发」）。 */
    public static class Outbox {

        /** 是否启用补发任务。 */
        private boolean enabled = true;

        /** 扫描周期（毫秒），默认 30 秒。 */
        private long intervalMs = 30_000L;

        /** 首次执行延迟（毫秒）。 */
        private long initialDelayMs = 15_000L;

        /** 每轮最多补发条数（22.10 纪律 2：单轮上限）。 */
        private int batchSize = 200;

        /** 最大投递次数，超过则置死信（status=2）。 */
        private int maxRetry = 3;

        /**
         * 指数退避间隔（秒），对应 19.6 的「1s / 5s / 30s」。
         * 长度即重试次数；索引 = retryCount-1，超出取最后一个。
         */
        private long[] backoffSeconds = {1L, 5L, 30L};

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }

        public long[] getBackoffSeconds() {
            return backoffSeconds;
        }

        public void setBackoffSeconds(long[] backoffSeconds) {
            this.backoffSeconds = backoffSeconds;
        }
    }

    /** 消费端参数。 */
    public static class Consumer {

        /** 每个队列的并发消费线程数。 */
        private int consumeThreadMin = 5;

        private int consumeThreadMax = 20;

        /** 单批最大消息数。 */
        private int consumeMessageBatchMaxSize = 1;

        /** 消费失败的最大重试次数，超过进死信队列（19.6）。 */
        private int maxReconsumeTimes = 3;

        public int getConsumeThreadMin() {
            return consumeThreadMin;
        }

        public void setConsumeThreadMin(int consumeThreadMin) {
            this.consumeThreadMin = consumeThreadMin;
        }

        public int getConsumeThreadMax() {
            return consumeThreadMax;
        }

        public void setConsumeThreadMax(int consumeThreadMax) {
            this.consumeThreadMax = consumeThreadMax;
        }

        public int getConsumeMessageBatchMaxSize() {
            return consumeMessageBatchMaxSize;
        }

        public void setConsumeMessageBatchMaxSize(int consumeMessageBatchMaxSize) {
            this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
        }

        public int getMaxReconsumeTimes() {
            return maxReconsumeTimes;
        }

        public void setMaxReconsumeTimes(int maxReconsumeTimes) {
            this.maxReconsumeTimes = maxReconsumeTimes;
        }
    }
}
