-- IM 消息全文检索（ngram，仅在未开启落库加密时使用）
SET @idx_ft_im := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND INDEX_NAME = 'ft_im_message_search'
);
SET @sql_ft := IF(
    @idx_ft_im = 0,
    'ALTER TABLE `im_message` ADD FULLTEXT INDEX `ft_im_message_search` (`content`, `file_name`) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE stmt_ft FROM @sql_ft;
EXECUTE stmt_ft;
DEALLOCATE PREPARE stmt_ft;

-- 统计快照按 metric_key + 日期读取
SET @idx_snapshot_metric_date := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_admin_statistic_snapshot'
      AND INDEX_NAME = 'idx_snapshot_metric_date'
);
SET @sql_snapshot := IF(
    @idx_snapshot_metric_date = 0,
    'CREATE INDEX `idx_snapshot_metric_date` ON `sys_admin_statistic_snapshot` (`metric_key`, `snapshot_date`)',
    'SELECT 1'
);
PREPARE stmt_snapshot FROM @sql_snapshot;
EXECUTE stmt_snapshot;
DEALLOCATE PREPARE stmt_snapshot;
