-- 用户服务（mall-user）：用户档案
-- 与 mall_auth.user_account 通过 user_id 一一对应，认证数据不落本库（见需求文档 ADR-009 / 26.3）
CREATE DATABASE IF NOT EXISTS `mall_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mall_user`;

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID，雪花生成，与 mall_auth.user_account.user_id 一致',
  `username`   VARCHAR(32) NOT NULL COMMENT '登录名（与认证库保持一致）',
  `nickname`   VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `phone`      VARCHAR(32) NOT NULL COMMENT '手机号明文，展示时按 21.4 脱敏',
  `level`      TINYINT NOT NULL DEFAULT 1 COMMENT '会员等级：1~5',
  `growth`     INT NOT NULL DEFAULT 0 COMMENT '成长值',
  `points`     INT NOT NULL DEFAULT 0 COMMENT '可用积分',
  `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0 禁用 1 正常',
  `deleted`    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户档案';