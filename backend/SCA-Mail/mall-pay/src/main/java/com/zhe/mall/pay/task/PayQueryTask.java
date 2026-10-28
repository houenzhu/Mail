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

import java.util.List;

/**
 * 支付查询兜底任务（ADR-023 / 22.10）。
 *
 * <p>回调是主路径，但可能丢失（网络抖动、渠道重试耗尽、回调地址配错）。本任务每分钟扫一遍
 * {@code status = 10 且未超时}的支付单，主动查渠道并对齐状态，把"用户已付款、订单仍待支付"这类
 * 静默不一致压到 1 分钟以内。
 *
 * <p>纪律（22.10 六条）：可开关、幂等、不跨服务长事务、可见（每轮打点）、
 * 多实例安全（{@code mall:lock:task:pay-query} 抢占）、可追溯（流水落 {@code payment_callback_log}）。
 * 逐条独立处理：单笔失败只 WARN，下轮重试。
 */
@Component
@ConditionalOnProperty(name = "mall.pay.query.enabled", havingValue = "true", matchIfMissing = true)
public class PayQueryTask {

    private static final Logger log = LoggerFactory.getLogger(PayQueryTask.class);

    private static final String TASK_NAME = "pay-query";

    private final PaymentService paymentService;

    private final DistributedLock distributedLock;

    private final PayProperties properties;

    public PayQueryTask(PaymentService paymentService, DistributedLock distributedLock, PayProperties properties) {
        this.paymentService = paymentService;
        this.distributedLock = distributedLock;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${mall.pay.query.initial-delay-ms:60000}",
            fixedDelayString = "${mall.pay.query.interval-ms:60000}")
    public void alignPaymentStatus() {
        long intervalMs = properties.getQuery().getIntervalMs();
        String lockKey = MallCacheKeys.taskLock(TASK_NAME);
        String token = distributedLock.tryLock(lockKey, intervalMs * 2, 0L, 0L);
        if (token == null) {
            log.debug("支付查询任务未抢到锁，跳过本轮: key={}", lockKey);
            return;
        }
        try {
            List<String> candidates = paymentService.findQueryCandidates(properties.getQuery().getBatchSize());
            if (candidates.isEmpty()) {
                return;
            }
            int aligned = 0;
            int failed = 0;
            for (String paymentNo : candidates) {
                try {
                    paymentService.sync(paymentNo);
                    aligned++;
                } catch (Exception e) {
                    failed++;
                    log.warn("支付查询对齐失败: paymentNo={}, reason={}", paymentNo, e.getMessage());
                }
            }
            log.info("支付查询任务完成: candidates={}, aligned={}, failed={}",
                    candidates.size(), aligned, failed);
        } catch (Exception e) {
            log.error("支付查询任务执行失败", e);
        } finally {
            distributedLock.unlock(lockKey, token);
        }
    }
}
