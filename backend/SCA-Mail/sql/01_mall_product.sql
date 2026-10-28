CREATE DATABASE IF NOT EXISTS `mall_product` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mall_product`;

CREATE TABLE IF NOT EXISTS `category` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID，0 表示顶级',
  `name`       VARCHAR(64) NOT NULL COMMENT '分类名称',
  `level`      TINYINT NOT NULL DEFAULT 1 COMMENT '层级：1 一级 2 二级 3 三级',
  `sort_no`    INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0 停用 1 启用',
  `deleted`    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_name` (`parent_id`, `name`),
  KEY `idx_parent_status` (`parent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类';

CREATE TABLE IF NOT EXISTS `brand` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '品牌ID',
  `name`        VARCHAR(64) NOT NULL COMMENT '品牌名称',
  `logo_url`    VARCHAR(255) DEFAULT NULL COMMENT '品牌LOGO',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '品牌描述',
  `sort_no`     INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0 停用 1 启用',
  `deleted`     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌';

CREATE TABLE IF NOT EXISTS `product` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID（SPU）',
  `name`        VARCHAR(128) NOT NULL COMMENT '商品名称',
  `subtitle`    VARCHAR(255) DEFAULT NULL COMMENT '副标题/卖点',
  `category_id` BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
  `brand_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '品牌ID',
  `main_image`  VARCHAR(255) DEFAULT NULL COMMENT '主图',
  `price_min`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '最低SKU价（列表展示用，冗余）',
  `price_max`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '最高SKU价（列表展示用，冗余）',
  `sales`       INT NOT NULL DEFAULT 0 COMMENT '累计销量',
  `detail`      MEDIUMTEXT COMMENT '图文详情',
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 下架 1 上架',
  `deleted`     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_category_status` (`category_id`, `status`, `deleted`),
  KEY `idx_brand_status` (`brand_id`, `status`, `deleted`),
  KEY `idx_status_updated` (`status`, `updated_at`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SPU';

CREATE TABLE IF NOT EXISTS `product_sku` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id`   BIGINT UNSIGNED NOT NULL COMMENT '所属商品ID',
  `sku_code`     VARCHAR(64) NOT NULL COMMENT 'SKU编码（业务唯一）',
  `name`         VARCHAR(128) NOT NULL COMMENT 'SKU名称',
  `spec_json`    JSON DEFAULT NULL COMMENT '规格快照，如 {"颜色":"红","尺码":"XL"}',
  `price`        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '售价',
  `market_price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '划线价',
  `image`        VARCHAR(255) DEFAULT NULL COMMENT 'SKU图片',
  `status`       TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0 停用 1 启用',
  `deleted`      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_status` (`product_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU（库存数据在 mall_inventory）';
