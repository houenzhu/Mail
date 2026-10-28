package com.zhe.mall.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code mall.pay.*} 配置（22.9 清单）。
 *
 * <p>{@code channel.secret} 属于敏感配置：生产必须走环境变量 / 配置中心，禁止入库、禁止打日志（28.4）。
 */
@Component
@ConfigurationProperties(prefix = "mall.pay")
public class PayProperties {

    private Channel channel = new Channel();

    private Callback callback = new Callback();

    private Query query = new Query();

    private CloseExpired closeExpired = new CloseExpired();

    private Reconcile reconcile = new Reconcile();

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public CloseExpired getCloseExpired() {
        return closeExpired;
    }

    public void setCloseExpired(CloseExpired closeExpired) {
        this.closeExpired = closeExpired;
    }

    public Reconcile getReconcile() {
        return reconcile;
    }

    public void setReconcile(Reconcile reconcile) {
        this.reconcile = reconcile;
    }

    /** 渠道配置。 */
    public static class Channel {

        private String code = "MOCK";

        private String secret = "";

        private String notifyUrl;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getNotifyUrl() {
            return notifyUrl;
        }

        public void setNotifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
        }
    }

    /** 回调时间窗配置。 */
    public static class Callback {

        private long toleranceSeconds = 300;

        public long getToleranceSeconds() {
            return toleranceSeconds;
        }

        public void setToleranceSeconds(long toleranceSeconds) {
            this.toleranceSeconds = toleranceSeconds;
        }
    }

    /** 查询兜底任务配置。 */
    public static class Query {

        private boolean enabled = true;

        private long intervalMs = 60000;

        private long initialDelayMs = 60000;

        private int batchSize = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /** 超时关闭任务配置。 */
    public static class CloseExpired {

        private boolean enabled = true;

        private long intervalMs = 60000;

        private long initialDelayMs = 30000;

        private int batchSize = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /** 对账任务配置。 */
    public static class Reconcile {

        private boolean enabled = true;

        private String cron = "0 0 2 * * ?";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
