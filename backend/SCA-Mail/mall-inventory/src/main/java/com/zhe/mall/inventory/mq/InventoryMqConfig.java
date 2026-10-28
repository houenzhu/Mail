package com.zhe.mall.inventory.mq;

import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.mq.MessageCodec;
import com.zhe.mall.common.mq.MessageEnvelope;
import com.zhe.mall.common.mq.MessageHandler;
import com.zhe.mall.common.mq.MessageTopics;
import com.zhe.mall.common.mq.RocketMqConsumerRegistrar;
import com.zhe.mall.inventory.service.InventoryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 库存服务的消息订阅（需求文档 19.6）。
 *
 * <p>本轮只订阅 {@code TOPIC_STOCK_ROLLBACK}：订单取消/关单后的库存归还。
 * 这闭合了 20.4 里「取消时库存回滚失败 → 订单已取消但库存未还」的缺口 ——
 * 原先同步 Feign 失败只留一行 WARN，现在由 MQ 重投直到成功。
 *
 * <p><b>幂等是双层的</b>：{@code consumed_message} 拦住重复投递（由
 * {@link RocketMqConsumerRegistrar} 统一处理），而最终权威是 {@code inventory_record}
 * 的 {@code uk_order_sku_type(order_no, sku_id, type)} —— 因此即使消息被多次投递、
 * 或与取消路径的同步回滚同时发生，也不会重复加库存。
 *
 * <p>订阅在 {@code @PostConstruct} 登记、由 mall-common 的
 * {@code ApplicationReadyEvent} 监听器统一启动：登记必须早于启动，
 * 否则订阅会被漏掉。
 */
@Configuration
public class InventoryMqConfig {

    private static final Logger log = LoggerFactory.getLogger(InventoryMqConfig.class);

    private final RocketMqConsumerRegistrar registrar;

    private final InventoryService inventoryService;

    public InventoryMqConfig(RocketMqConsumerRegistrar registrar, InventoryService inventoryService) {
        this.registrar = registrar;
        this.inventoryService = inventoryService;
    }

    @PostConstruct
    public void subscribe() {
        registrar.subscribe("stock-rollback", MessageTopics.STOCK_ROLLBACK, MessageTopics.TAG_ROLLBACK,
                this::handleStockRollback);
    }

    /**
     * 处理库存回滚事件。
     *
     * @return true 表示全部明细回滚成功（acknowledge）；false 交给容器重投
     */
    private boolean handleStockRollback(MessageEnvelope envelope) {
        MessageEvents.StockRollbackEvent payload =
                MessageCodec.payloadOf(envelope, MessageEvents.StockRollbackEvent.class);
        if (payload.items() == null || payload.items().isEmpty()) {
            log.warn("库存回滚事件没有明细，跳过: orderNo={}, msgId={}", payload.orderNo(), envelope.msgId());
            return true;
        }
        boolean allOk = true;
        for (MessageEvents.Item item : payload.items()) {
            // 复用既有回滚逻辑：内部是「幂等流水 + 条件更新」，重复调用安全
            var result = inventoryService.rollback(
                    new DeductRequest(payload.orderNo(), item.skuId(), item.quantity()));
            if (result == null || !result.success()) {
                allOk = false;
                log.warn("库存回滚事件处理未成功，等待重投: orderNo={}, skuId={}, qty={}, message={}",
                        payload.orderNo(), item.skuId(), item.quantity(),
                        result == null ? "无响应" : result.message());
            }
        }
        if (allOk) {
            log.info("库存回滚事件处理成功: orderNo={}, skuCount={}, reason={}",
                    payload.orderNo(), payload.items().size(), payload.reason());
        }
        return allOk;
    }
}
