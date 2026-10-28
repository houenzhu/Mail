package com.zhe.mall.common.mq;

/**
 * 消息处理器：由各服务的消费者实现。
 *
 * <p>约定：<b>不要在这个方法里吞异常</b>。返回 false 或抛异常都会让容器按
 * {@code maxReconsumeTimes} 重投，最终进死信队列；吞掉异常会导致消息静默丢失。
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * 处理一条消息。
     *
     * @param envelope 已解析的统一信封（messageId / topic / key / traceId / payload）
     * @return true 表示处理成功（acknowledge）；false 表示失败，交给容器重投
     */
    boolean handle(MessageEnvelope envelope) throws Exception;
}
