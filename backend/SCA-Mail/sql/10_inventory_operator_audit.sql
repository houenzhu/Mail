USE `mall_inventory`;

-- MySQL 8.0.32 没有 ALTER TABLE ADD COLUMN IF NOT EXISTS 语法，按 information_schema
-- 检查后分别添加，支持已有数据库重复执行。操作人只取网关注入的可信身份。
SET @operator_id_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_record'
    AND column_name = 'operator_id'
);
SET @operator_id_ddl := IF(
  @operator_id_exists = 0,
  'ALTER TABLE `inventory_record` ADD COLUMN `operator_id` BIGINT UNSIGNED NULL COMMENT ''操作人用户 ID（由网关可信身份透传）'' AFTER `remark`',
  'SELECT ''operator_id already exists'''
);
PREPARE operator_id_stmt FROM @operator_id_ddl;
EXECUTE operator_id_stmt;
DEALLOCATE PREPARE operator_id_stmt;

SET @operator_name_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_record'
    AND column_name = 'operator_name'
);
SET @operator_name_ddl := IF(
  @operator_name_exists = 0,
  'ALTER TABLE `inventory_record` ADD COLUMN `operator_name` VARCHAR(64) NULL COMMENT ''操作人用户名（由网关注入可信身份）'' AFTER `operator_id`',
  'SELECT ''operator_name already exists'''
);
PREPARE operator_name_stmt FROM @operator_name_ddl;
EXECUTE operator_name_stmt;
DEALLOCATE PREPARE operator_name_stmt;
