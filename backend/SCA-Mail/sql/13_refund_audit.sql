USE mall_pay;

-- 退款审核流（12.24 / 28.12）：记录"谁审核、何时审核"。
-- 刻意不复用 operator / operator_id：那两列是"发起方"（买家 / 客服），
-- 覆盖它会让审计链丢掉原始申请人，正好是资金纠纷最需要的信息。
-- MySQL 8.0.32 不支持 ADD COLUMN IF NOT EXISTS，沿用 10/11/12 的
-- information_schema 检查方式，可重复执行。
SET @audited_by_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund' AND column_name = 'audited_by'
);
SET @audited_by_ddl := IF(
  @audited_by_exists = 0,
  'ALTER TABLE refund ADD COLUMN audited_by VARCHAR(32) NULL COMMENT ''审核人账号（后台 staff）'' AFTER operator_id',
  'SELECT ''audited_by already exists'''
);
PREPARE audited_by_stmt FROM @audited_by_ddl;
EXECUTE audited_by_stmt;
DEALLOCATE PREPARE audited_by_stmt;

SET @audited_by_id_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund' AND column_name = 'audited_by_id'
);
SET @audited_by_id_ddl := IF(
  @audited_by_id_exists = 0,
  'ALTER TABLE refund ADD COLUMN audited_by_id BIGINT UNSIGNED NULL COMMENT ''审核人用户 ID'' AFTER audited_by',
  'SELECT ''audited_by_id already exists'''
);
PREPARE audited_by_id_stmt FROM @audited_by_id_ddl;
EXECUTE audited_by_id_stmt;
DEALLOCATE PREPARE audited_by_id_stmt;

SET @audited_at_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund' AND column_name = 'audited_at'
);
SET @audited_at_ddl := IF(
  @audited_at_exists = 0,
  'ALTER TABLE refund ADD COLUMN audited_at DATETIME(3) NULL COMMENT ''审核时间'' AFTER audited_by_id',
  'SELECT ''audited_at already exists'''
);
PREPARE audited_at_stmt FROM @audited_at_ddl;
EXECUTE audited_at_stmt;
DEALLOCATE PREPARE audited_at_stmt;