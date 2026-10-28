package com.zhe.mall.pay.task;

import com.zhe.mall.common.cache.DistributedLock;
import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.pay.config.PayProperties;
import com.zhe.mall.pay.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付单超时关闭任务（28.8）。
 *
 * <p>把 {@code status = 0/10 且 expire_at < now} 的支付单 CAS 到 40 已关闭。它是"用户重复发起支付"
 * 与"订单超时关单"之间的清理器：避免长期堆积永远不可能成功的支付单。
 *
 * <p>注意这里是<b>清理</b>而不是<b>判定</b>——订单是否可以取消由订单侧的状态机决定；
 * 支付已完成却被关闭的竞争窗口在 20.4 已登记（关单前复核支付单状态，第 7 项 MQ 化时处理）。
 */
@Component
@ConditionalOnProperty(name = "mall.pay.close-expired.enabled", havingValue = "true", matchIfMissing = true)
public class PayCloseExpiredTask {

    private static final Logger log = LoggerFactory.getLogger(PayCloseExpiredTask.class);

    private static final String TASK_NAME = "pay-close-expired";

    private final PaymentService paymentService;

    private final DistributedLock distributedLock;

    private final PayProperties properties;

    public PayCloseExpiredTask(PaymentService paymentService, DistributedLock distributedLock,
                               PayProperties properties) {
        this.paymentService = paymentService;
        this.distributedLock = distributedLock;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${mall.pay.close-expired.initial-delay-ms:30000}",
            fixedDelayString = "${mall.pay.close-expired.interval-ms:60000}")
    public void closeExpiredPayments() {
        long intervalMs = properties.getCloseExpired().getIntervalMs();
        String lockKey = MallCacheKeys.taskLock(TASK_NAME);
        String token = distributedLock.tryLock(lockKey, intervalMs * 2, 0L, 0L);
        if (token == null) {
            log.debug("支付单超时关闭任务未抢到锁，跳过本轮: key={}", lockKey);
            return;
        }
        try {
            int closed = paymentService.closeExpired(properties.getCloseExpired().getBatchSize());
            if (closed > 0) {
                log.info("支付单超时关闭完成: closed={}", closed);
            }
        } catch (Exception e) {
            log.error("支付单超时关闭任务执行失败", e);
        } finally {
            distributedLock.unlock(lockKey, token);
        }
    }
}
