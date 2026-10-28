-- 认证服务（mall-auth）：账号凭证、刷新令牌审计、登录审计
-- 只存认证与风控字段；昵称/等级等展示档案在 mall_user.user（见需求文档 ADR-009 / 26.3）
-- V1.10（Sa-Token 迁移，见需求文档 21.9）：
--   令牌与账号会话的**在线判定以 Redis db1 的 `satoken:*` 为准**，本库不再承担判定职责；
--   `refresh_token` 降级为审计流水（只存令牌 SHA-256 指纹前 32 位，不存原始令牌）；
--   `token_blacklist` 已废弃（注销改为直接删除 Sa-Token 会话），保留仅作历史归档。
CREATE DATABASE IF NOT EXISTS `mall_auth` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mall_auth`;

CREATE TABLE IF NOT EXISTS `user_account` (
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID，雪花生成，与 mall_user.user.id 一致',
  `username`      VARCHAR(32) NOT NULL COMMENT '登录名',
  `phone`         VARCHAR(32) NOT NULL COMMENT '手机号明文，展示与日志按 21.4 脱敏',
  `phone_hash`    CHAR(64) NOT NULL COMMENT 'SHA-256(phone + salt)，用于唯一索引与按手机号查询',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 口令散列，强度 10（21.4）',
  `nickname`      VARCHAR(64) DEFAULT NULL COMMENT '昵称（注册时同步给 mall_user）',
  `roles`         VARCHAR(128) NOT NULL DEFAULT 'USER' COMMENT '角色，逗号分隔：USER/OPERATOR/CS/ADMIN',
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0 注销 1 正常 2 锁定',
  `fail_count`    INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_until`    DATETIME(3) DEFAULT NULL COMMENT '锁定截止时间，NULL 表示未锁定',
  `last_login_at` DATETIME(3) DEFAULT NULL COMMENT '最近登录时间',
  `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone_hash` (`phone_hash`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号凭证';

CREATE TABLE IF NOT EXISTS `refresh_token` (
  `jti`        CHAR(32) NOT NULL COMMENT '刷新令牌 SHA-256 指纹前 32 位（V1.10 起不存原始令牌）',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `issued_at`  DATETIME(3) NOT NULL COMMENT '签发时间',
  `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间',
  `revoked`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否已撤销：0 有效 1 已撤销（旋转或注销）',
  `rotated_to` CHAR(32) DEFAULT NULL COMMENT '旋转后新令牌的指纹前 32 位，便于追踪令牌链',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`jti`),
  KEY `idx_user_revoked` (`user_id`, `revoked`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='刷新令牌审计流水（ADR-010 / 21.9.3：V1.10 起只存指纹，在线判定以 Sa-Token 会话为准）';

CREATE TABLE IF NOT EXISTS `token_blacklist` (
  `jti`        CHAR(32) NOT NULL COMMENT '访问令牌 jti',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `expires_at` DATETIME(3) NOT NULL COMMENT '令牌自然过期时间，过期后可清理',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`jti`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='访问令牌注销黑名单【已废弃 V1.10：注销改为删除 Sa-Token 会话，本表不再写入】';

CREATE TABLE IF NOT EXISTS `login_log` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID，账号不存在时为空',
  `username`   VARCHAR(32) NOT NULL COMMENT '登录名',
  `login_ip`   VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
  `user_agent` VARCHAR(255) DEFAULT NULL COMMENT 'User-Agent',
  `result`     TINYINT NOT NULL COMMENT '结果：1 成功 2 密码错误 3 账号锁定 4 账号不存在 5 已注销',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_username_created` (`username`, `created_at`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录审计';
