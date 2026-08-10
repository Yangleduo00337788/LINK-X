-- 作者：yangleduo
-- =============================================================================
-- V2: 好友关系表增加分组名（存量库补丁；新库 init.sql 已含该列）
-- =============================================================================
SET @exist := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user_relation'
    AND COLUMN_NAME = 'group_name'
);
SET @sql := IF(
  @exist = 0,
  'ALTER TABLE `sys_user_relation` ADD COLUMN `group_name` varchar(32) DEFAULT NULL COMMENT ''好友分组名'' AFTER `remark`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
