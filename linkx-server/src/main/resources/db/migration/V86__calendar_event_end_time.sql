-- 作者：yangleduo
SET @end_time_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'calendar_event'
    AND COLUMN_NAME = 'end_time'
);

SET @ddl := IF(
  @end_time_exists = 0,
  'ALTER TABLE calendar_event ADD COLUMN end_time varchar(5) DEFAULT NULL COMMENT ''结束时间 HH:mm'' AFTER time',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
