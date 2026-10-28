package com.zhe.mall.pay.task;

import com.zhe.mall.common.cache.DistributedLock;
import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.pay.config.PayProperties;
import com.zhe.mall.pay.dto.ReconcileResultVO;
import com.zhe.mall.pay.service.PayReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日支付对账任务（28.8 / 22.10「支付对账」）。
 *
 * <p>默认 02:00 跑，对账的是<b>前一天</b>：凌晨执行时"当天"还没结束，拿一个未走完的日期去对账
 * 只会产出大量假差异（渠道还没出账的流水会被算成 LOCAL_ONLY）。
 *
 * <p>差异落 {@code pay_reconcile_diff} 并打 ERROR 日志 + 指标 {@code mall_pay_reconcile_diff_total}，
 * 由人工处理清单（22.10 要求"可追溯"）。手工触发见 {@code POST /api/pay/internal/reconcile/{bizDate}}。
 */
@Component
@ConditionalOnProperty(name = "mall.pay.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class PayReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(PayReconcileTask.class);

    private static final String TASK_NAME = "pay-reconcile";

    /** 锁 TTL：对账是分钟级任务，给足 30 分钟余量 */
    private static final long LOCK_TTL_MILLIS = 30 * 60 * 1000L;

    private final PayReconcileService payReconcileService;

    private final DistributedLock distributedLock;

    public PayReconcileTask(PayReconcileService payReconcileService, DistributedLock distributedLock) {
        this.payReconcileService = payReconcileService;
        this.distributedLock = distributedLock;
    }

    @Scheduled(cron = "${mall.pay.reconcile.cron:0 0 2 * * ?}")
    public void reconcileDaily() {
        String lockKey = MallCacheKeys.taskLock(TASK_NAME);
        String token = distributedLock.tryLock(lockKey, LOCK_TTL_MILLIS, 0L, 0L);
        if (token == null) {
            log.debug("支付对账任务未抢到锁，跳过本轮: key={}", lockKey);
            return;
        }
        try {
            LocalDate bizDate = LocalDate.now().minusDays(1);
            ReconcileResultVO result = payReconcileService.reconcile(bizDate);
            log.info("支付对账任务完成: bizDate={}, channelCount={}, localCount={}, diffCount={}",
                    result.bizDate(), result.channelCount(), result.localCount(), result.diffCount());
        } catch (Exception e) {
            log.error("支付对账任务执行失败", e);
        } finally {
            distributedLock.unlock(lockKey, token);
        }
    }
}
