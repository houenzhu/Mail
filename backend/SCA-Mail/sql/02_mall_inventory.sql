CREATE DATABASE IF NOT EXISTS `mall_inventory` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mall_inventory`;

CREATE TABLE IF NOT EXISTS `inventory` (
  `sku_id`         BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID（与 mall_product.product_sku.id 对应）',
  `total`          INT NOT NULL DEFAULT 0 COMMENT '总库存',
  `locked`         INT NOT NULL DEFAULT 0 COMMENT '已锁定数量（预扣未落库）',
  `available`      INT NOT NULL DEFAULT 0 COMMENT '可售库存',
  `warn_threshold` INT NOT NULL DEFAULT 0 COMMENT '库存预警阈值',
  `version`        INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`sku_id`),
  KEY `idx_available` (`available`),
  CONSTRAINT `chk_inventory_available` CHECK (`available` >= 0),
  CONSTRAINT `chk_inventory_locked` CHECK (`locked` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU库存';

CREATE TABLE IF NOT EXISTS `inventory_record` (
  `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花ID，应用生成）',
  `sku_id`     BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
  `order_no`   VARCHAR(32) NOT NULL COMMENT '关联订单号；预扣场景可填 requestId',
  `type`       TINYINT NOT NULL COMMENT '类型：1 扣减 2 回滚 3 预扣 4 预扣回滚',
  `qty`        INT NOT NULL COMMENT '变动数量（正数）',
  `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0 处理中 1 成功 2 失败',
  `remark`     VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sku_type` (`order_no`, `sku_id`, `type`),
  KEY `idx_sku_created` (`sku_id`, `created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存变动流水（幂等与对账依据）';
