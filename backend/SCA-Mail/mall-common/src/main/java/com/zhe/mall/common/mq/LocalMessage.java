package com.zhe.mall.common.mq;

import java.time.LocalDateTime;

/**
 * 本地消息表记录（Outbox，需求文档 19.6 / 20.3）。
 *
 * <p>与业务写同库同事务插入，因此「业务提交成功」必然蕴含「消息已落库」，这是 RPO=0 的依据（6.2）。
 * 表结构见 {@code sql/09_mall_mq.sql}。
 *
 * <p>这里用可变 JavaBean 而非 record：MyBatis 需要无参构造 + setter/反射赋值。
 */
public class LocalMessage {

    /** 状态：待发送。 */
    public static final int STATUS_PENDING = 0;

    /** 状态：已发送。 */
    public static final int STATUS_SENT = 1;

    /** 状态：死信（超过最大重试次数）。 */
    public static final int STATUS_DEAD = 2;

    private Long id;

    private String msgId;

    private String topic;

    private String tag;

    private String msgKey;

    private String bizType;

    private String bizId;

    /** 统一事件信封的 JSON。 */
    private String payload;

    private String traceId;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private Integer delayLevel;

    private String errorMsg;

    private LocalDateTime sentAt;

    /** 认领者标识（实例标识），用于多实例下避免同一行被重复投递。 */
    private String owner;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public void setMsgKey(String msgKey) {
        this.msgKey = msgKey;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Integer getDelayLevel() {
        return delayLevel;
    }

    public void setDelayLevel(Integer delayLevel) {
        this.delayLevel = delayLevel;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
