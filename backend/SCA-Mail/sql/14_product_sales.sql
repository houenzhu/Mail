-- 累计销量回写（G-12）。
--
-- 认领表让"订单已支付 → 商品销量累加"具备业务级幂等：
-- 同一 orderNo + productId 只允许一条记录，MQ 重投/人工重放都不会重复加销量。
-- 不依赖 consumed_message：那张表按 msgId 去重，补发产生新 msgId 时挡不住重复累加。
SET NAMES utf8mb4;

USE `mall_product`;

CREATE TABLE IF NOT EXISTS `product_sales_record` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`   VARCHAR(32) NOT NULL COMMENT '订单号（业务幂等键之一）',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID（业务幂等键之一）',
  `quantity`   INT NOT NULL COMMENT '本次计入的销量件数',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_product` (`order_no`, `product_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='累计销量回写认领表（幂等）';

-- 消费幂等表：mall-product 首次成为 MQ 消费方（TOPIC_ORDER_PAID）。
-- 结构与 09_mall_mq.sql 完全一致，必须落在消费方自己的库里。
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
