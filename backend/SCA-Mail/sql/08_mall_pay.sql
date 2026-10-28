-- 支付服务（mall-pay）：支付单、回调流水、退款单、对账差异（见需求文档 28.3）
-- 跨库只存业务号（order_no / user_id），不做外键；资金相关字段一律 DECIMAL(12,2)，禁止浮点。
-- 幂等三道防线：Redis 幂等标记（拦截层）→ 本脚本唯一索引（权威）→ 状态 CAS（并发兜底）。
CREATE DATABASE IF NOT EXISTS `mall_pay` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mall_pay`;

-- 28.3.1 支付单：一个订单可有多张支付单（1:N，ADR-020），同一订单只允许一张未关闭支付单（28.5 CAS 保证）
CREATE TABLE IF NOT EXISTS `payment` (
  `payment_no`       VARCHAR(32) NOT NULL COMMENT '支付单号，应用生成：P + 雪花',
  `order_no`         VARCHAR(32) NOT NULL COMMENT '订单号（跨库只存业务号，不做外键）',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '下单用户，鉴权用',
  `request_id`       VARCHAR(64) NOT NULL COMMENT '客户端幂等号，与 order_no 组成唯一键',
  `channel`          VARCHAR(16) NOT NULL COMMENT '渠道码：MOCK（规划 WECHAT / ALIPAY）',
  `pay_type`         TINYINT NOT NULL COMMENT '与 orders.pay_type 对齐：1 微信 2 支付宝 3 余额',
  `amount`           DECIMAL(12,2) NOT NULL COMMENT '支付金额，必须等于订单 pay_amount',
  `status`           TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 待支付 10 支付中 20 支付成功 30 支付失败 40 已关闭 50 已退款（16.5）',
  `channel_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '渠道流水号，回调回填，与 payment_no 一同用于对账',
  `prepay_id`        VARCHAR(128) DEFAULT NULL COMMENT '渠道预支付标识（Mock 通道为随机串）',
  `paid_at`          DATETIME(3) DEFAULT NULL COMMENT '支付成功时间',
  `closed_at`        DATETIME(3) DEFAULT NULL COMMENT '关闭时间',
  `expire_at`        DATETIME(3) DEFAULT NULL COMMENT '支付单有效期（= 订单 expire_at，与超时关单对齐）',
  `fail_reason`      VARCHAR(128) DEFAULT NULL COMMENT '失败原因（渠道码或文案）',
  `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`payment_no`),
  UNIQUE KEY `uk_order_request` (`order_no`, `request_id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_status_expire` (`status`, `expire_at`),
  KEY `idx_channel_trade` (`channel`, `channel_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单（ADR-020：订单 1:N 支付单）';

-- 28.3.2 回调 / 查询流水：审计 + 防重放（先落流水再做业务，28.6 纪律 1）
CREATE TABLE IF NOT EXISTS `payment_callback_log` (
  `id`               BIGINT UNSIGNED NOT NULL COMMENT '雪花 ID',
  `payment_no`       VARCHAR(32) DEFAULT NULL COMMENT '支付单号（解析不出时为空，仍要留痕）',
  `channel`          VARCHAR(16) NOT NULL COMMENT '渠道码',
  `event_type`       VARCHAR(16) NOT NULL COMMENT '事件类型：PAY_CALLBACK / REFUND_CALLBACK / QUERY',
  `channel_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '渠道流水号，防重放唯一键的一半',
  `amount`           DECIMAL(12,2) DEFAULT NULL COMMENT '回调金额',
  `sign_valid`       TINYINT NOT NULL DEFAULT 0 COMMENT '验签是否通过：0 否 1 是',
  `process_result`   TINYINT NOT NULL COMMENT '处理结果：1 成功 2 验签失败 3 重复事件 4 金额不一致 5 状态非法 6 对账差异',
  `raw_body`         VARCHAR(1024) DEFAULT NULL COMMENT '原始报文（截断保存，禁止记录渠道密钥）',
  `remote_ip`        VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
  `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '到达时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event` (`channel`, `channel_trade_no`, `event_type`),
  KEY `idx_payment_created` (`payment_no`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回调 / 查询流水：审计与防重放';

-- 28.3.3 退款单：幂等关键在 active_key——处理中/成功时为 payment_no，失败置 NULL
-- （MySQL 唯一索引允许多个 NULL，因此"同一支付单最多一条有效退款单、失败后可重试"由索引天然保证，ADR-024）
CREATE TABLE IF NOT EXISTS `refund` (
  `refund_no`         VARCHAR(32) NOT NULL COMMENT '退款单号：R + 雪花',
  `payment_no`        VARCHAR(32) NOT NULL COMMENT '关联支付单',
  `order_no`          VARCHAR(32) NOT NULL COMMENT '关联订单号',
  `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '下单用户',
  `amount`            DECIMAL(12,2) NOT NULL COMMENT '退款金额（本阶段 = 支付金额，部分退款第四阶段）',
  `status`            TINYINT NOT NULL COMMENT '状态：0 待审核 10 退款中 20 退款成功 30 退款失败（16.5）',
  `active_key`        VARCHAR(32) DEFAULT NULL COMMENT '幂等键：处理中/成功时为 payment_no，失败置 NULL 允许重试',
  `reason`            VARCHAR(128) DEFAULT NULL COMMENT '退款原因',
  `operator`          VARCHAR(32) NOT NULL COMMENT '发起方：user / cs / admin（21.7 审计要求）',
  `operator_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人 ID',
  `channel_refund_no` VARCHAR(64) DEFAULT NULL COMMENT '渠道退款单号',
  `applied_at`        DATETIME(3) DEFAULT NULL COMMENT '申请时间',
  `finished_at`       DATETIME(3) DEFAULT NULL COMMENT '完成时间',
  `fail_reason`       VARCHAR(128) DEFAULT NULL COMMENT '失败原因',
  `created_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`refund_no`),
  UNIQUE KEY `uk_active` (`active_key`),
  KEY `idx_payment` (`payment_no`),
  KEY `idx_order` (`order_no`),
  KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单（ADR-024：全额退款 + active_key 幂等）';

-- 28.3.4 对账差异清单：重复跑不重复落（uk_diff），清单人工处理
CREATE TABLE IF NOT EXISTS `pay_reconcile_diff` (
  `id`               BIGINT UNSIGNED NOT NULL COMMENT '雪花 ID',
  `biz_date`         DATE NOT NULL COMMENT '对账业务日',
  `payment_no`       VARCHAR(32) DEFAULT NULL COMMENT '差异对象：支付单号',
  `channel_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '差异对象：渠道流水号',
  `diff_type`        VARCHAR(24) NOT NULL COMMENT 'CHANNEL_ONLY / LOCAL_ONLY / AMOUNT_MISMATCH / STATUS_MISMATCH',
  `detail`           VARCHAR(512) DEFAULT NULL COMMENT '差异明细（两侧状态与金额）',
  `status`           TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0 待处理 1 已处理 2 已忽略',
  `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_diff` (`biz_date`, `payment_no`, `diff_type`),
  KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差异清单';
