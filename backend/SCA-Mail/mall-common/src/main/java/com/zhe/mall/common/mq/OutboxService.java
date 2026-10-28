package com.zhe.mall.common.mq;

import com.zhe.mall.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Outbox 写入器：把「要发的消息」与业务写放进同一个本地事务（19.6 / 20.3）。
 *
 * <p>调用时机很关键：必须在业务事务<b>内</b>调用。业务提交 → 消息已落库；业务回滚 → 消息一起消失。
 * 这正是 RPO=0 的实现方式，也是本类不自己开事务的原因。
 *
 * <p>写入时就把信封序列化好：投递是异步的（补发任务在另一个线程/进程），届时 MDC 里的 traceId
 * 早已不在，因此必须在<b>当前请求线程</b>上取 traceId 并固化进 payload。
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    /** RocketMQ 固定延时级别 16 = 30 分钟，与 19.6 的超时关单口径一致。 */
    public static final int DELAY_LEVEL_30_MINUTES = 16;

    private final LocalMessageMapper mapper;

    private final String producerName;

    public OutboxService(LocalMessageMapper mapper, Environment environment) {
        this.mapper = mapper;
        this.producerName = environment.getProperty("spring.application.name", "unknown-service");
    }

    /**
     * 记录一条待投递事件（每次调用生成新 msgId）。
     *
     * @param topic      Topic，取 {@link MessageTopics} 常量
     * @param tag        Tag，可为 null
     * @param key        分区键（业务键），保证同一对象顺序消费
     * @param bizType    业务类型，排障用
     * @param bizId      业务主键（orderNo / paymentNo），排障用
     * @param payload    业务负载对象，会被序列化为 JSON
     * @param delayLevel RocketMQ 延时级别；0 表示立即发送
     * @return 落库的 Outbox 记录（含 msgId）
     */
    public LocalMessage record(String topic, String tag, String key,
                               String bizType, String bizId,
                               Object payload, int delayLevel) {
        LocalMessage row = buildMessage(topic, tag, key, bizType, bizId, payload, delayLevel, newMsgId());
        mapper.insert(row);
        if (log.isDebugEnabled()) {
            log.debug("Outbox 已登记: msgId={}, topic={}, bizType={}, bizId={}, delayLevel={}",
                    row.getMsgId(), row.getTopic(), row.getBizType(), row.getBizId(), row.getDelayLevel());
        }
        return row;
    }

    /**
     * 记录一条事件，**同一业务事件只落一行**（按 {@code dedupeKey} 派生确定性 msgId）。
     *
     * <p>用途：幂等重放的路径（重复回调、查询兜底重放）每次都会调用本方法，
     * 若每次生成新 msgId 会堆出大量重复行。用确定性 msgId 后，
     * 重复调用撞 {@code uk_msg_id} 被忽略 —— 语义是"这件事的通知只发一次"，
     * 与 19.6「幂等键：回调类用 paymentNo」的口径一致。
     *
     * @param dedupeKey 业务幂等键（如 paymentNo）。同一 (业务类型, dedupeKey) 只产生一条消息
     * @return 本次是否真的插入了新行（false 表示已存在，忽略）
     */
    public boolean recordOnce(String bizType, String dedupeKey, String topic, String tag, String key,
                              String bizId, Object payload, int delayLevel) {
        String msgId = deterministicMsgId(bizType, dedupeKey);
        LocalMessage row = buildMessage(topic, tag, key, bizType, bizId, payload, delayLevel, msgId);
        try {
            mapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            // 已存在同业务事件的通知，忽略即可（这正是幂等的目的）
            log.debug("Outbox 事件已存在，忽略重复登记: bizType={}, dedupeKey={}, msgId={}",
                    bizType, dedupeKey, msgId);
            return false;
        }
    }

    /** 立即投递的事件。 */
    public LocalMessage record(String topic, String tag, String key,
                               String bizType, String bizId, Object payload) {
        return record(topic, tag, key, bizType, bizId, payload, 0);
    }

    private LocalMessage buildMessage(String topic, String tag, String key,
                                      String bizType, String bizId,
                                      Object payload, int delayLevel, String msgId) {
        MessageEnvelope envelope = new MessageEnvelope(
                msgId,
                topic,
                tag,
                key,
                currentTraceId(),
                LocalDateTime.now(),
                producerName,
                MessageEnvelope.CURRENT_VERSION,
                MessageCodec.toJson(payload));

        LocalMessage row = new LocalMessage();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setMsgId(msgId);
        row.setTopic(topic);
        row.setTag(tag);
        row.setMsgKey(key);
        row.setBizType(bizType);
        row.setBizId(bizId);
        row.setPayload(MessageCodec.toJson(envelope));
        row.setTraceId(envelope.traceId());
        row.setStatus(LocalMessage.STATUS_PENDING);
        row.setRetryCount(0);
        // 立即可投递：next_retry_at 设为当前时间，补发任务下一轮就会取到
        row.setNextRetryAt(LocalDateTime.now());
        row.setDelayLevel(delayLevel);
        return row;
    }

    private int insert(LocalMessage row) {
        mapper.insert(row);
        if (log.isDebugEnabled()) {
            log.debug("Outbox 已登记: msgId={}, topic={}, bizType={}, bizId={}, delayLevel={}",
                    row.getMsgId(), row.getTopic(), row.getBizType(), row.getBizId(), row.getDelayLevel());
        }
        return 1;
    }

    private String newMsgId() {
        return SnowflakeIdGenerator.nextIdStr();
    }

    /**
     * 由 (业务类型, 幂等键) 派生确定性 msgId。
     *
     * <p>用 SHA-256 前 16 字节的十六进制（32 字符）而不是直接拼接：
     * 业务键可能很长或含分隔符，固定长度能塞进 {@code msg_id VARCHAR(64)} 且不产生歧义。
     */
    private String deterministicMsgId(String bizType, String dedupeKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest((bizType + "|" + dedupeKey).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", raw[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // 理论上不会发生；退化到随机 ID（宁可重复通知，也不要丢通知）
            log.warn("生成确定性 msgId 失败，退化为随机 ID: bizType={}, error={}", bizType, e.getMessage());
            return newMsgId();
        }
    }

    /**
     * 当前 MDC 中的 traceId（由 {@code UserContextFilter} 写入）。
     *
     * <p>定时任务等无请求上下文的场景取不到，返回 null —— 信封允许 traceId 为空。
     */
    private String currentTraceId() {
        return Optional.ofNullable(MDC.get("traceId")).filter(s -> !s.isBlank()).orElse(null);
    }
}
