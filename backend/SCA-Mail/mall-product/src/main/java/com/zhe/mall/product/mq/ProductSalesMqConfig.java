package com.zhe.mall.product.mq;

import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.mq.MessageCodec;
import com.zhe.mall.common.mq.MessageEnvelope;
import com.zhe.mall.common.mq.MessageTopics;
import com.zhe.mall.common.mq.RocketMqConsumerRegistrar;
import com.zhe.mall.product.service.ProductSalesService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 商品服务的消息订阅：订单已支付 → 累计销量回写（G-12）。
 *
 * <p>幂等是双层的：{@code consumed_message} 拦住同一 msgId 的重复投递，
 * 业务侧再由 {@code product_sales_record(order_no, product_id)} 唯一键兜底，
 * 因此补发/人工重放产生新 msgId 也不会重复加销量。
 *
 * <p>订阅在 {@code @PostConstruct} 登记、由 mall-common 的
 * {@code ApplicationReadyEvent} 监听器统一启动（原因见 {@link RocketMqConsumerRegistrar}）。
 */
@Configuration
public class ProductSalesMqConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductSalesMqConfig.class);

    private final RocketMqConsumerRegistrar registrar;

    private final ProductSalesService salesService;

    public ProductSalesMqConfig(RocketMqConsumerRegistrar registrar, ProductSalesService salesService) {
        this.registrar = registrar;
        this.salesService = salesService;
    }

    @PostConstruct
    public void subscribe() {
        registrar.subscribe("order-paid-sales", MessageTopics.ORDER_PAID, MessageTopics.TAG_ORDER_PAID,
                this::handleOrderPaid);
    }

    private boolean handleOrderPaid(MessageEnvelope envelope) {
        MessageEvents.OrderPaidEvent payload =
                MessageCodec.payloadOf(envelope, MessageEvents.OrderPaidEvent.class);
        try {
            return salesService.applyOrderPaid(payload);
        } catch (Exception e) {
            log.error("销量回写失败，将重投: orderNo={}, msgId={}",
                    payload == null ? null : payload.orderNo(), envelope.msgId(), e);
            return false;
        }
    }
}
