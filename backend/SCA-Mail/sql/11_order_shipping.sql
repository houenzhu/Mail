USE `mall_order`;

-- UC-ORDER-07 发货：补物流字段（MySQL 8.0.32 不支持 ADD COLUMN IF NOT EXISTS，
-- 沿用 10_inventory_operator_audit.sql 的 information_schema 检查方式，可重复执行）。
SET @shipping_company_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'shipping_company'
);
SET @shipping_company_ddl := IF(
  @shipping_company_exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `shipping_company` VARCHAR(64) NULL COMMENT ''物流公司'' AFTER `cancel_reason`',
  'SELECT ''shipping_company already exists'''
);
PREPARE shipping_company_stmt FROM @shipping_company_ddl;
EXECUTE shipping_company_stmt;
DEALLOCATE PREPARE shipping_company_stmt;

SET @tracking_no_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'tracking_no'
);
SET @tracking_no_ddl := IF(
  @tracking_no_exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `tracking_no` VARCHAR(64) NULL COMMENT ''物流单号'' AFTER `shipping_company`',
  'SELECT ''tracking_no already exists'''
);
PREPARE tracking_no_stmt FROM @tracking_no_ddl;
EXECUTE tracking_no_stmt;
DEALLOCATE PREPARE tracking_no_stmt;

SET @shipped_at_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'shipped_at'
);
SET @shipped_at_ddl := IF(
  @shipped_at_exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `shipped_at` DATETIME(3) NULL COMMENT ''发货时间'' AFTER `tracking_no`',
  'SELECT ''shipped_at already exists'''
);
PREPARE shipped_at_stmt FROM @shipped_at_ddl;
EXECUTE shipped_at_stmt;
DEALLOCATE PREPARE shipped_at_stmt;

SET @shipped_by_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'shipped_by'
);
SET @shipped_by_ddl := IF(
  @shipped_by_exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `shipped_by` VARCHAR(64) NULL COMMENT ''发货操作人用户名（由网关可信身份透传）'' AFTER `shipped_at`',
  'SELECT ''shipped_by already exists'''
);
PREPARE shipped_by_stmt FROM @shipped_by_ddl;
EXECUTE shipped_by_stmt;
DEALLOCATE PREPARE shipped_by_stmt;
