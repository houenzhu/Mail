package com.zhe.mall.order.mq;

import com.zhe.mall.api.dto.PayCallbackRequest;
import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.mq.MessageCodec;
import com.zhe.mall.common.mq.MessageEnvelope;
import com.zhe.mall.common.mq.MessageTopics;
import com.zhe.mall.common.mq.RocketMqConsumerRegistrar;
import com.zhe.mall.order.service.OrderService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 订单服务的消息订阅（需求文档 19.6 / 29.5）。
 *
 * <p>本轮订阅 {@code TOPIC_PAY_SUCCESS}：**这是修复「支付成功但订单停在待支付」的关键一环**。
 *
 * <p>链路：mall-pay 支付成功 → 同步内部回调（快路径，零延迟）+ 落 `TOPIC_PAY_SUCCESS` 事件（保险）
 * → 若同步失败，事件由 Outbox 补发任务重投 → 本消费者调用 {@link OrderService#payCallback}。
 * 由于 {@code payCallback} 自身幂等（已是 PAID 直接返回当前订单），
 * **"事件先到、同步后到"或"同步已成功、事件随后到"都不会产生重复流转**，
 * 因此可以放心让两条路径并存（这也是 MQ 化能平滑落地的前提）。
 *
 * <p>幂等是双层的：{@code consumed_message} 拦住重复投递（由 {@link RocketMqConsumerRegistrar}
 * 统一处理），业务侧再由订单状态 CAS 兜底。
 *
 * <p>订阅在 {@code @PostConstruct} 登记、由 mall-common 的 {@code ApplicationReadyEvent}
 * 监听器统一启动：登记必须早于启动，否则订阅会被漏掉。
 */
@Configuration
public class OrderMqConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderMqConfig.class);

    private final RocketMqConsumerRegistrar registrar;

    private final OrderService orderService;

    public OrderMqConfig(RocketMqConsumerRegistrar registrar, OrderService orderService) {
        this.registrar = registrar;
        this.orderService = orderService;
    }

    @PostConstruct
    public void subscribe() {
        registrar.subscribe("pay-success", MessageTopics.PAY_SUCCESS, MessageTopics.TAG_PAID,
                this::handlePaySuccess);
    }

    /**
     * 处理支付成功事件。
     *
     * <p>复用 {@code OrderService.payCallback}：它已经做了金额校验、状态机校验与 CAS 幂等，
     * 因此重复投递、乱序（同步回调先成功）都安全。
     *
     * @return true 表示已受理（或已支付，无需再处理）；false 交给容器重投
     */
    private boolean handlePaySuccess(MessageEnvelope envelope) {
        MessageEvents.PaySuccessEvent payload =
                MessageCodec.payloadOf(envelope, MessageEvents.PaySuccessEvent.class);
        String orderNo = payload.orderNo();
        if (orderNo == null || orderNo.isBlank()) {
            log.error("支付成功事件缺少 orderNo，已丢弃: msgId={}, paymentNo={}",
                    envelope.msgId(), payload.paymentNo());
            return true;
        }
        try {
            var view = orderService.payCallback(orderNo,
                    new PayCallbackRequest(payload.paymentNo(), payload.payType(), payload.payAmount()));
            log.info("支付成功事件处理完成: orderNo={}, paymentNo={}, status={}({})",
                    orderNo, payload.paymentNo(), view.status(), view.statusLabel());
            return true;
        } catch (Exception e) {
            // 抛出去交给容器按 maxReconsumeTimes 重投，最终进死信（不吞异常）
            log.error("支付成功事件处理失败，将重投: orderNo={}, paymentNo={}, msgId={}",
                    orderNo, payload.paymentNo(), envelope.msgId(), e);
            return false;
        }
    }
}
