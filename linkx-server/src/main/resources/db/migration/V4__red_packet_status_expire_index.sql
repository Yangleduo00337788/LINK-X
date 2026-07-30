-- =============================================================================
-- V4: 红包过期扫描索引（status + expire_time），加速定时任务分批 FOR UPDATE
-- =============================================================================
SET @exist := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'red_packet'
    AND INDEX_NAME = 'idx_status_expire'
);
SET @sql := IF(
  @exist = 0,
  'ALTER TABLE `red_packet` ADD INDEX `idx_status_expire` (`status`, `expire_time`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
