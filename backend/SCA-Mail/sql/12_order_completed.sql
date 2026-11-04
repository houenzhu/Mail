USE `mall_order`;

-- UC-ORDER-06 确认收货：补"完成时间"字段。
-- 完成时间是售后期起算与结算对账的锚点，不能只靠 updated_at 推断
-- （任何后续更新都会覆盖它）。
-- MySQL 8.0.32 不支持 ADD COLUMN IF NOT EXISTS，沿用 10/11 的
-- information_schema 检查方式，可重复执行。
SET @completed_at_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'completed_at'
);
SET @completed_at_ddl := IF(
  @completed_at_exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `completed_at` DATETIME(3) NULL COMMENT ''完成时间（确认收货）'' AFTER `shipped_by`',
  'SELECT ''completed_at already exists'''
);
PREPARE completed_at_stmt FROM @completed_at_ddl;
EXECUTE completed_at_stmt;
DEALLOCATE PREPARE completed_at_stmt;

-- 已完成订单按完成时间倒序展示；仅对非空值建索引，避免拖慢无关查询。
SET @idx_completed_at_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_completed_at'
);
SET @idx_completed_at_ddl := IF(
  @idx_completed_at_exists = 0,
  'ALTER TABLE `orders` ADD INDEX `idx_orders_completed_at` (`completed_at`)',
  'SELECT ''idx_orders_completed_at already exists'''
);
PREPARE idx_completed_at_stmt FROM @idx_completed_at_ddl;
EXECUTE idx_completed_at_stmt;
DEALLOCATE PREPARE idx_completed_at_stmt;
