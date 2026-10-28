package com.zhe.mall.common.mq;

import com.zhe.mall.common.cache.DistributedLock;
import com.zhe.mall.common.cache.MallCacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地消息表补发任务（20.3「本地消息表补发」/ 22.10）。
 *
 * <p>职责：扫描 {@code local_message} 里待发送的行，投递到 RocketMQ，成功后置已发送；
 * 失败按 1s / 5s / 30s 指数退避重试，超过上限置死信（status=2）并打 ERROR 告警。
 *
 * <p><b>为什么自带调度线程而不是用 {@code @Scheduled}</b>：本类在 mall-common，
 * 会被所有引入 rocketmq-client 的服务装配。用 {@code @Scheduled} 就要求每个服务都加
 * {@code @EnableScheduling}，否则 Bean 静默不执行（很难发现的坑）。自带单线程调度器
 * 只占用一个线程，把「任务是否真的跑起来」变成显式可控。
 *
 * <p>遵守 22.10 六条纪律：
 * <ol>
 *   <li>可开关：{@code mall.mq.outbox.enabled}；</li>
 *   <li>幂等：单行条件更新（status 0 → 1），重复投递只影响 0 行；单轮上限 {@code batchSize}；</li>
 *   <li>不跨服务：只读本库 Outbox + 发 MQ；</li>
 *   <li>可见：每轮打点（候选 / 成功 / 失败 / 死信）；</li>
 *   <li>多实例安全：Redis 抢占锁 + 行级 owner 认领双保险；</li>
 *   <li>可追溯：msgId 与业务键都落库。</li>
 * </ol>
 */
public class OutboxRelayTask implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayTask.class);

    private static final String TASK_NAME = "outbox-relay";

    private final LocalMessageMapper mapper;

    private final RocketMqProducer producer;

    private final MallMqProperties properties;

    private final ObjectProvider<DistributedLock> lockProvider;

    /** 本实例标识，用于行级认领。 */
    private final String instanceId = "outbox-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    private ScheduledExecutorService scheduler;

    public OutboxRelayTask(LocalMessageMapper mapper,
                           RocketMqProducer producer,
                           MallMqProperties properties,
                           ObjectProvider<DistributedLock> lockProvider) {
        this.mapper = mapper;
        this.producer = producer;
        this.properties = properties;
        this.lockProvider = lockProvider;
    }

    /** 启动调度（幂等：重复调用只生效一次）。 */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        if (!properties.getOutbox().isEnabled()) {
            log.warn("Outbox 补发任务已关闭（mall.mq.outbox.enabled=false），消息将只在业务写时落库、不投递");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        long initialDelay = properties.getOutbox().getInitialDelayMs();
        long interval = properties.getOutbox().getIntervalMs();
        scheduler.scheduleWithFixedDelay(this::relaySafely, initialDelay, interval, TimeUnit.MILLISECONDS);
        log.info("Outbox 补发任务已启动: instance={}, initialDelay={}ms, interval={}ms, batchSize={}, maxRetry={}",
                instanceId, initialDelay, interval,
                properties.getOutbox().getBatchSize(), properties.getOutbox().getMaxRetry());
    }

    private void relaySafely() {
        try {
            relay();
        } catch (Throwable t) {
            // 调度线程绝不能被异常杀死，否则任务会静默停摆
            log.error("Outbox 补发任务未捕获异常（已忽略，下轮继续）", t);
        }
    }

    /** 执行一轮补发；包可见以便测试直接调用。 */
    void relay() {
        MallMqProperties.Outbox outbox = properties.getOutbox();

        // 多实例纪律 5：拿不到锁的实例跳过本轮。
        // Redis 不可用时锁拿不到 → 退化为「行级 owner 认领」兜底，而不是整体停摆
        DistributedLock lock = lockProvider.getIfAvailable();
        String lockKey = MallCacheKeys.taskLock(TASK_NAME);
        long lockTtl = Math.max(30_000L, outbox.getIntervalMs() * 2);
        String token = lock == null ? null : lock.tryLock(lockKey, lockTtl, 0L, 0L);
        if (lock != null && token == null) {
            log.debug("Outbox 补发任务未抢到锁，跳过本轮: key={}", lockKey);
            return;
        }

        int sent = 0;
        int failed = 0;
        int dead = 0;
        try {
            List<LocalMessage> candidates = mapper.selectPending(LocalDateTime.now(), outbox.getBatchSize());
            if (candidates.isEmpty()) {
                return;
            }
            for (LocalMessage row : candidates) {
                // 行级认领：两个实例同时扫到同一行时只有一个能更新成功
                if (mapper.claim(row.getId(), instanceId) == 0) {
                    continue;
                }
                try {
                    if (deliver(row)) {
                        mapper.markSent(row.getId(), LocalDateTime.now());
                        sent++;
                    } else {
                        failed++;
                        dead += scheduleRetryOrDead(row, outbox, "broker 未确认（SEND_OK 缺失）");
                    }
                } catch (Exception e) {
                    failed++;
                    dead += scheduleRetryOrDead(row, outbox, e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
            if (dead > 0) {
                log.error("Outbox 补发出现死信: 本轮新增={}, 累计死信={}, 需人工处理（20.3）",
                        dead, mapper.countDead());
            }
            log.info("Outbox 补发完成: candidates={}, sent={}, failed={}, dead={}",
                    candidates.size(), sent, failed, dead);
        } finally {
            if (lock != null && token != null) {
                lock.unlock(lockKey, token);
            }
        }
    }

    /** 投递一条 Outbox 记录；信封已序列化好，这里只负责发。 */
    private boolean deliver(LocalMessage row) throws Exception {
        return producer.send(row.getTopic(), row.getTag(), row.getMsgKey(), row.getPayload(),
                row.getDelayLevel() == null ? 0 : row.getDelayLevel());
    }

    /**
     * 失败处理：未达上限 → 指数退避后重试；已达上限 → 置死信。
     *
     * @return 1 表示本轮置为死信，0 表示仅安排了重试
     */
    private int scheduleRetryOrDead(LocalMessage row, MallMqProperties.Outbox outbox, String error) {
        int nextRetryCount = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
        String message = truncate(error, 500);
        if (nextRetryCount >= outbox.getMaxRetry()) {
            mapper.markDead(row.getId(), message);
            log.error("Outbox 消息投递失败达上限，置死信: msgId={}, topic={}, bizId={}, retryCount={}, error={}",
                    row.getMsgId(), row.getTopic(), row.getBizId(), nextRetryCount, message);
            return 1;
        }
        mapper.markRetry(row.getId(),
                LocalDateTime.now().plusSeconds(backoffSeconds(outbox, nextRetryCount)), message);
        log.warn("Outbox 消息投递失败，安排第 {} 次重试: msgId={}, topic={}, bizId={}, error={}",
                nextRetryCount, row.getMsgId(), row.getTopic(), row.getBizId(), message);
        return 0;
    }

    private long backoffSeconds(MallMqProperties.Outbox outbox, int retryCount) {
        long[] backoff = outbox.getBackoffSeconds();
        if (backoff == null || backoff.length == 0) {
            return 5L;
        }
        int index = Math.min(Math.max(0, retryCount - 1), backoff.length - 1);
        return Math.max(1L, backoff[index]);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
