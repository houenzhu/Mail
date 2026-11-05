-- 收货地址簿（UC-USER-02）：mall_user.user_address
--
-- 设计要点：
--   1) 默认地址唯一：MySQL 普通唯一索引无法表达"最多一个 1、多个 0"，
--      用生成列 default_user_id = IF(is_default=1, user_id, NULL) 落到唯一索引：
--      NULL 不参与唯一性，因此同一用户只能有 1 条默认地址，且天然按用户隔离。
--   2) 逻辑删除：deleted=1；删除前必须把 is_default 置 0，
--      否则残留的 is_default=1 会占住 uk_default_user，导致无法再设置默认地址。
--   3) 不收地址 ID 作为下单入参：订单侧快照收货信息（BR-09），改地址不影响历史订单。
SET NAMES utf8mb4;

USE `mall_user`;

CREATE TABLE IF NOT EXISTS `user_address` (
  `id`              BIGINT UNSIGNED NOT NULL COMMENT '地址ID（雪花生成）',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID（归属校验以此为准）',
  `receiver_name`   VARCHAR(64) NOT NULL COMMENT '收货人',
  `receiver_phone`  VARCHAR(20) NOT NULL COMMENT '收货电话',
  `region`          VARCHAR(120) NOT NULL DEFAULT '' COMMENT '省市区文本（如 广东省广州市天河区）',
  `detail_address`  VARCHAR(255) NOT NULL COMMENT '详细地址（街道/门牌）',
  `is_default`      TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认：0 否 1 是（每用户最多 1 条）',
  `default_user_id` BIGINT UNSIGNED GENERATED ALWAYS AS (IF(`is_default` = 1, `user_id`, NULL)) STORED
                     COMMENT '默认地址唯一键：is_default=1 时等于 user_id，否则 NULL',
  `deleted`         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_default_user` (`default_user_id`),
  KEY `idx_user_deleted` (`user_id`, `deleted`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址簿';
