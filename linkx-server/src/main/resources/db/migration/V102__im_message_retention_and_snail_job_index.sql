-- 消息留存清理：WHERE deleted = 0 AND create_time < ? 可走索引
SET @idx_im_retention := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND INDEX_NAME = 'idx_im_message_deleted_create_time'
);
SET @sql_im := IF(
    @idx_im_retention = 0,
    'CREATE INDEX `idx_im_message_deleted_create_time` ON `im_message` (`deleted`, `create_time`)',
    'SELECT 1'
);
PREPARE stmt_im FROM @sql_im;
EXECUTE stmt_im;
DEALLOCATE PREPARE stmt_im;

-- Snail Job 监控：按 execution_at 范围聚合（表由 Snail Job 服务创建，可能尚未存在）
SET @tbl_sj_batch := (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'snail_job'
      AND TABLE_NAME = 'sj_job_task_batch'
);
SET @idx_sj_batch := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'snail_job'
      AND TABLE_NAME = 'sj_job_task_batch'
      AND INDEX_NAME = 'idx_deleted_execution_at'
);
SET @sql_sj := IF(
    @tbl_sj_batch > 0 AND @idx_sj_batch = 0,
    'CREATE INDEX `idx_deleted_execution_at` ON `snail_job`.`sj_job_task_batch` (`deleted`, `execution_at`)',
    'SELECT 1'
);
PREPARE stmt_sj FROM @sql_sj;
EXECUTE stmt_sj;
DEALLOCATE PREPARE stmt_sj;
