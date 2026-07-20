-- 账号安全扩展：手机号字段
-- 邮箱字段已在 init.sql 中按需添加

SET @exist_phone := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'phone'
);
SET @sql_phone := IF(
  @exist_phone = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `phone` varchar(20) DEFAULT NULL COMMENT ''手机号'' AFTER `email`',
  'SELECT 1'
);
PREPARE stmt FROM @sql_phone;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 手机号唯一索引（允许多个 NULL）
SET @exist_uk := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_phone'
);
SET @sql_uk := IF(
  @exist_uk = 0,
  'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_phone` (`phone`)',
  'SELECT 1'
);
PREPARE stmt2 FROM @sql_uk;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
