package com.zhe.mall.order.task;

import com.zhe.mall.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付超时兜底扫描：每分钟扫一次 status = 10 且 expire_at 已过期的订单并取消。
 * 第三阶段接入 RocketMQ 延时消息后该任务仅作为兜底保留。
 */
@Component
@ConditionalOnProperty(name = "mall.order.timeout-scan.enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    private static final int SCAN_BATCH_SIZE = 200;

    private final OrderService orderService;

    public OrderTimeoutTask(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "${mall.order.timeout-scan.initial-delay-ms:30000}",
            fixedDelayString = "${mall.order.timeout-scan.interval-ms:60000}")
    public void cancelTimeoutOrders() {
        List<String> orderNos;
        try {
            orderNos = orderService.findTimeoutOrderNos(SCAN_BATCH_SIZE);
        } catch (Exception e) {
            log.error("订单超时扫描失败", e);
            return;
        }
        if (orderNos.isEmpty()) {
            return;
        }
        int cancelled = 0;
        for (String orderNo : orderNos) {
            try {
                orderService.cancelBySystem(orderNo, orderService.timeoutCancelReason());
                cancelled++;
            } catch (Exception e) {
                log.warn("超时取消订单失败: orderNo={}, reason={}", orderNo, e.getMessage());
            }
        }
        log.info("订单超时扫描完成: candidates={}, cancelled={}", orderNos.size(), cancelled);
    }
}
