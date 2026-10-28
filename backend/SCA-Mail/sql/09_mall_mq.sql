-- RocketMQ 接入（第三阶段第 7 项）：本地消息表（Outbox） + 消费幂等表（见需求文档 19.6 / 20.3）
-- 设计要点：
--   1) Outbox 与业务写同库同事务 —— 业务提交则消息必不丢，是 RPO=0 的依据（6.2）；
--   2) 独立线程扫描待发送记录投递 MQ，成功后置 sent；失败累计 3 次进死信（status=2）；
--   3) 消费端以 msg_id 唯一键兜底重复投递，业务唯一索引是第二道防线（19.6「消费」）。
-- 幂等：本脚本 CREATE TABLE IF NOT EXISTS，可重复执行，不删除已有数据。
--
-- SET NAMES 必须放在最前：mysql.exe 的 source 会按客户端字符集解释文件字节，
-- 中文注释若不显式声明会被按 GBK 误解码，在 information_schema 里变成乱码。
SET NAMES utf8mb4;
--
-- 注意：**不建独立的 mall_mq 库**。Outbox 必须与业务表同库同事务，否则会出现
-- 「业务已提交、消息未落库」的窗口，RPO=0 即失效（6.2 / 19.6）。因此 local_message
-- 落在各自服务的库里（mall_order / mall_pay）；消费幂等表落在消费方的库里。

-- ---------------------------------------------------------------------------
-- 1) 本地消息表：每个「生产方」一个库内的表，结构完全一致
-- ---------------------------------------------------------------------------

USE `mall_order`;

CREATE TABLE IF NOT EXISTS `local_message` (
  `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花ID，应用生成）',
  `msg_id`         VARCHAR(64)  NOT NULL COMMENT '消息全局唯一 ID（雪花），消费端幂等键',
  `topic`          VARCHAR(64)  NOT NULL COMMENT 'Topic',
  `tag`            VARCHAR(64)  DEFAULT NULL COMMENT 'Tag，可为空',
  `msg_key`        VARCHAR(128) NOT NULL COMMENT '分区键（业务键，保证同一对象顺序消费）',
  `biz_type`       VARCHAR(32)  NOT NULL COMMENT '业务类型：ORDER_CREATED / ORDER_TIMEOUT / ORDER_CANCELLED / STOCK_ROLLBACK',
  `biz_id`         VARCHAR(64)  NOT NULL COMMENT '业务主键（orderNo），排障与对账用',
  `payload`        TEXT         NOT NULL COMMENT '统一事件信封的 JSON 序列化结果',
  `trace_id`       VARCHAR(64)  DEFAULT NULL COMMENT '生成该消息时的 MDC traceId，便于全链路排查',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 死信',
  `retry_count`    INT          NOT NULL DEFAULT 0 COMMENT '已投递失败次数，达上限（默认 3）置死信',
  `next_retry_at`  DATETIME(3)  NOT NULL COMMENT '下次可投递时间（指数退避 1s/5s/30s）',
  `delay_level`    INT          NOT NULL DEFAULT 0 COMMENT 'RocketMQ 延时级别：0 立即，16 = 30 分钟（19.6 超时关单）',
  `error_msg`      VARCHAR(512) DEFAULT NULL COMMENT '最后一次失败原因',
  `sent_at`        DATETIME(3)  DEFAULT NULL COMMENT '投递成功时间',
  `owner`          VARCHAR(64)  DEFAULT NULL COMMENT '认领者（实例标识），多实例下防止同一行被重复投递',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`),
  KEY `idx_status_retry` (`status`, `next_retry_at`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地消息表（Outbox，20.3 补发任务扫描源）';

-- mall_pay 库（结构一致；订单侧与支付侧各自独立，避免跨库事务）
USE `mall_pay`;

CREATE TABLE IF NOT EXISTS `local_message` (
  `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花ID，应用生成）',
  `msg_id`         VARCHAR(64)  NOT NULL COMMENT '消息全局唯一 ID（雪花），消费端幂等键',
  `topic`          VARCHAR(64)  NOT NULL COMMENT 'Topic',
  `tag`            VARCHAR(64)  DEFAULT NULL COMMENT 'Tag，可为空',
  `msg_key`        VARCHAR(128) NOT NULL COMMENT '分区键（业务键，保证同一对象顺序消费）',
  `biz_type`       VARCHAR(32)  NOT NULL COMMENT '业务类型：PAY_SUCCESS / REFUND_RESULT',
  `biz_id`         VARCHAR(64)  NOT NULL COMMENT '业务主键（paymentNo / refundNo），排障与对账用',
  `payload`        TEXT         NOT NULL COMMENT '统一事件信封的 JSON 序列化结果',
  `trace_id`       VARCHAR(64)  DEFAULT NULL COMMENT '生成该消息时的 MDC traceId，便于全链路排查',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 死信',
  `retry_count`    INT          NOT NULL DEFAULT 0 COMMENT '已投递失败次数，达上限（默认 3）置死信',
  `next_retry_at`  DATETIME(3)  NOT NULL COMMENT '下次可投递时间（指数退避 1s/5s/30s）',
  `delay_level`    INT          NOT NULL DEFAULT 0 COMMENT 'RocketMQ 延时级别：0 立即',
  `error_msg`      VARCHAR(512) DEFAULT NULL COMMENT '最后一次失败原因',
  `sent_at`        DATETIME(3)  DEFAULT NULL COMMENT '投递成功时间',
  `owner`          VARCHAR(64)  DEFAULT NULL COMMENT '认领者（实例标识），多实例下防止同一行被重复投递',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`),
  KEY `idx_status_retry` (`status`, `next_retry_at`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地消息表（Outbox，20.3 补发任务扫描源）';

-- ---------------------------------------------------------------------------
-- 2) 消费幂等表：每个「消费方」一个库内的表（order / inventory / pay）
--    语义：先插入 msg_id 成功者才执行业务（status=0 处理中）；
--          处理成功改 status=1；处理失败**删除该行**，让 broker 重投能重新认领。
--    这样既挡住并发重复消费，又不会把"一次失败"变成"永久跳过"。
-- ---------------------------------------------------------------------------

USE `mall_inventory`;

CREATE TABLE IF NOT EXISTS `consumed_message` (
  `msg_id`       VARCHAR(64)  NOT NULL COMMENT '已消费的消息 ID（生产端雪花）',
  `topic`        VARCHAR(64)  NOT NULL COMMENT 'Topic',
  `consumer`     VARCHAR(64)  NOT NULL COMMENT '消费方标识（服务名:业务名）',
  `dedup_key`    VARCHAR(160) DEFAULT NULL COMMENT '业务幂等键（如 orderNo），排障用',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 处理中 1 成功（失败则删除本行）',
  `error_msg`    VARCHAR(512) DEFAULT NULL COMMENT '处理失败原因（重投成功后保留最后一次）',
  `consumed_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`msg_id`),
  KEY `idx_consumer_time` (`consumer`, `consumed_at`),
  KEY `idx_status` (`status`, `consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费幂等表：msg_id 唯一，插入成功者才处理';

USE `mall_order`;

CREATE TABLE IF NOT EXISTS `consumed_message` (
  `msg_id`       VARCHAR(64)  NOT NULL COMMENT '已消费的消息 ID（生产端雪花）',
  `topic`        VARCHAR(64)  NOT NULL COMMENT 'Topic',
  `consumer`     VARCHAR(64)  NOT NULL COMMENT '消费方标识（服务名:业务名）',
  `dedup_key`    VARCHAR(160) DEFAULT NULL COMMENT '业务幂等键（如 orderNo），排障用',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 处理中 1 成功（失败则删除本行）',
  `error_msg`    VARCHAR(512) DEFAULT NULL COMMENT '处理失败原因（重投成功后保留最后一次）',
  `consumed_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`msg_id`),
  KEY `idx_consumer_time` (`consumer`, `consumed_at`),
  KEY `idx_status` (`status`, `consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费幂等表：msg_id 唯一，插入成功者才处理';

USE `mall_pay`;

CREATE TABLE IF NOT EXISTS `consumed_message` (
  `msg_id`       VARCHAR(64)  NOT NULL COMMENT '已消费的消息 ID（生产端雪花）',
  `topic`        VARCHAR(64)  NOT NULL COMMENT 'Topic',
  `consumer`     VARCHAR(64)  NOT NULL COMMENT '消费方标识（服务名:业务名）',
  `dedup_key`    VARCHAR(160) DEFAULT NULL COMMENT '业务幂等键（如 orderNo），排障用',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 处理中 1 成功（失败则删除本行）',
  `error_msg`    VARCHAR(512) DEFAULT NULL COMMENT '处理失败原因（重投成功后保留最后一次）',
  `consumed_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`msg_id`),
  KEY `idx_consumer_time` (`consumer`, `consumed_at`),
  KEY `idx_status` (`status`, `consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费幂等表：msg_id 唯一，插入成功者才处理';
