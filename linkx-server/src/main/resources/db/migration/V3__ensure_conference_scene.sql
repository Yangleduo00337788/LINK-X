-- =============================================================================
-- V3: 会议表增加 scene（存量库补丁；新库 init.sql 已含该列）
-- =============================================================================
SET @exist := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'conference'
    AND COLUMN_NAME = 'scene'
);
SET @sql := IF(
  @exist = 0,
  'ALTER TABLE `conference` ADD COLUMN `scene` varchar(16) NOT NULL DEFAULT ''meeting'' COMMENT ''场景: call=电话 meeting=会议'' AFTER `type`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
