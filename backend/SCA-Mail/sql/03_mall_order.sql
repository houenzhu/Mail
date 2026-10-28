CREATE DATABASE IF NOT EXISTS `mall_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mall_order`;

CREATE TABLE IF NOT EXISTS `orders` (
  `order_no`         VARCHAR(32) NOT NULL COMMENT '订单号（业务主键，雪花/时间戳生成）',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID（预留分片键）',
  `request_id`       VARCHAR(64) NOT NULL COMMENT '客户端幂等号',
  `status`           TINYINT NOT NULL DEFAULT 10 COMMENT '10 待支付 20 已支付 30 已发货 40 已完成 50 已取消 60 退款中 70 已退款',
  `total_amount`     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '商品总额',
  `discount_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  `pay_amount`       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额',
  `coupon_id`        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '优惠券ID',
  `pay_type`         TINYINT NOT NULL DEFAULT 0 COMMENT '0 未支付 1 微信 2 支付宝 3 余额',
  `receiver_name`    VARCHAR(64) NOT NULL DEFAULT '' COMMENT '收货人',
  `receiver_phone`   VARCHAR(20) NOT NULL DEFAULT '' COMMENT '收货电话',
  `receiver_address` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '收货地址',
  `remark`           VARCHAR(255) DEFAULT NULL COMMENT '买家备注',
  `expire_at`        DATETIME(3) NOT NULL COMMENT '支付超时时间',
  `paid_at`          DATETIME(3) DEFAULT NULL COMMENT '支付时间',
  `cancelled_at`     DATETIME(3) DEFAULT NULL COMMENT '取消时间',
  `cancel_reason`    VARCHAR(128) DEFAULT NULL COMMENT '取消原因',
  `deleted`          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`order_no`),
  UNIQUE KEY `uk_user_request` (`user_id`, `request_id`),
  KEY `idx_user_status` (`user_id`, `status`, `created_at`),
  KEY `idx_status_expire` (`status`, `expire_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表（预留按 user_id 分片）';

CREATE TABLE IF NOT EXISTS `order_item` (
  `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花ID，应用生成）',
  `order_no`     VARCHAR(32) NOT NULL COMMENT '订单号',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID（冗余分片键，保证与订单同分片）',
  `sku_id`       BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
  `product_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '商品ID',
  `product_name` VARCHAR(128) NOT NULL COMMENT '商品名称快照',
  `sku_spec`     VARCHAR(255) DEFAULT NULL COMMENT '规格快照',
  `price`        DECIMAL(12,2) NOT NULL COMMENT '下单时单价',
  `qty`          INT NOT NULL COMMENT '数量',
  `total_amount` DECIMAL(12,2) NOT NULL COMMENT '小计金额',
  `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_no`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细';

CREATE TABLE IF NOT EXISTS `order_status_log` (
  `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花ID，应用生成）',
  `order_no`    VARCHAR(32) NOT NULL COMMENT '订单号',
  `from_status` TINYINT NOT NULL COMMENT '原状态',
  `to_status`   TINYINT NOT NULL COMMENT '新状态',
  `operator`    VARCHAR(64) NOT NULL COMMENT '操作者：user / system / mq / pay',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_order_created` (`order_no`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单状态流转日志';
