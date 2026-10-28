package com.zhe.mall.common.mq;

import java.time.LocalDateTime;

/**
 * 统一事件信封（需求文档 19.6，关闭 D-04）。
 *
 * <p>所有 Topic 共用同一信封，业务差异只体现在 {@code payload} 与 {@code topic/tag} 上。
 * 这样消费端可以先用固定的信封字段做去重、路由与链路追踪，再按 payload 反序列化到具体业务类型。
 *
 * <p><b>字段只增不改语义</b>（19.6 兼容口径）：新增字段必须给默认值，删除字段需先停消费端。
 *
 * @param msgId      消息全局唯一 ID（雪花），消费端幂等键
 * @param topic      Topic，与 {@link MessageTopics} 一致
 * @param tag        Tag，可为 null
 * @param key        分区键；同一 key 进同一队列，保证同一业务对象顺序消费
 * @param traceId    gateway 生成的链路标识，生产端从 MDC 取
 * @param occurredAt 事件发生时间（Asia/Shanghai）
 * @param producer   生产方服务名（spring.application.name）
 * @param version    schema 版本，当前 1
 * @param payload    业务负载（JSON 字符串，按 topic 决定具体结构）
 */
public record MessageEnvelope(
        String msgId,
        String topic,
        String tag,
        String key,
        String traceId,
        LocalDateTime occurredAt,
        String producer,
        int version,
        String payload) {

    /** 当前 schema 版本（19.6）。 */
    public static final int CURRENT_VERSION = 1;

    public MessageEnvelope {
        if (version <= 0) {
            version = CURRENT_VERSION;
        }
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
