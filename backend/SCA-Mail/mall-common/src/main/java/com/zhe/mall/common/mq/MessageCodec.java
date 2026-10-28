package com.zhe.mall.common.mq;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 事件信封的 JSON 编解码（19.6）。
 *
 * <p>刻意与缓存用的 {@code MallCache.MAPPER} 分开：缓存值反序列化失败会被当作未命中并删键，
 * 而消息反序列化失败必须让消费端走重试/死信，两者的失败语义完全不同，不能共用一套配置。
 *
 * <p>Boot 4 使用 Jackson 3（{@code tools.jackson} 包名），与 Spring MVC 的转换器同源。
 */
public final class MessageCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // 新增字段必须能给默认值（19.6 兼容口径）：遇到未知字段不要让消费端崩
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private MessageCodec() {
    }

    /** 序列化任意负载为 JSON 字符串。 */
    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** 反序列化信封。 */
    public static MessageEnvelope envelopeFromJson(String json) {
        return MAPPER.readValue(json, MessageEnvelope.class);
    }

    /** 反序列化负载到具体业务类型。 */
    public static <T> T payloadFromJson(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    /** 把信封里的 payload 再解析一层到业务类型。 */
    public static <T> T payloadOf(MessageEnvelope envelope, Class<T> type) {
        return payloadFromJson(envelope.payload(), type);
    }
}
