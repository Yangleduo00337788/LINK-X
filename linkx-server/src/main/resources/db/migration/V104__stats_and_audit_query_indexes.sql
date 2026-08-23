-- 群会话统计、登录热力图、Snail Job 批次列表
SET @idx_im_conv_type := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_conversation'
      AND INDEX_NAME = 'idx_im_conv_type_deleted'
);
SET @sql_im_conv := IF(
    @idx_im_conv_type = 0,
    'CREATE INDEX `idx_im_conv_type_deleted` ON `im_conversation` (`type`, `deleted`, `id`)',
    'SELECT 1'
);
PREPARE stmt_im_conv FROM @sql_im_conv;
EXECUTE stmt_im_conv;
DEALLOCATE PREPARE stmt_im_conv;

SET @idx_icm_conv_deleted := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_conversation_member'
      AND INDEX_NAME = 'idx_icm_conv_deleted'
);
SET @sql_icm := IF(
    @idx_icm_conv_deleted = 0,
    'CREATE INDEX `idx_icm_conv_deleted` ON `im_conversation_member` (`conversation_id`, `deleted`)',
    'SELECT 1'
);
PREPARE stmt_icm FROM @sql_icm;
EXECUTE stmt_icm;
DEALLOCATE PREPARE stmt_icm;

SET @idx_login_success_time := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_login_audit'
      AND INDEX_NAME = 'idx_login_success_create_time'
);
SET @sql_login := IF(
    @idx_login_success_time = 0,
    'CREATE INDEX `idx_login_success_create_time` ON `sys_login_audit` (`success`, `create_time`)',
    'SELECT 1'
);
PREPARE stmt_login FROM @sql_login;
EXECUTE stmt_login;
DEALLOCATE PREPARE stmt_login;

SET @idx_sys_user_status := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND INDEX_NAME = 'idx_sys_user_status_deleted'
);
SET @sql_user := IF(
    @idx_sys_user_status = 0,
    'CREATE INDEX `idx_sys_user_status_deleted` ON `sys_user` (`status`, `deleted`)',
    'SELECT 1'
);
PREPARE stmt_user FROM @sql_user;
EXECUTE stmt_user;
DEALLOCATE PREPARE stmt_user;

SET @tbl_sj_batch := (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'snail_job'
      AND TABLE_NAME = 'sj_job_task_batch'
);
SET @idx_sj_job_batch := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'snail_job'
      AND TABLE_NAME = 'sj_job_task_batch'
      AND INDEX_NAME = 'idx_deleted_job_id'
);
SET @sql_sj := IF(
    @tbl_sj_batch > 0 AND @idx_sj_job_batch = 0,
    'CREATE INDEX `idx_deleted_job_id` ON `snail_job`.`sj_job_task_batch` (`deleted`, `job_id`, `id`)',
    'SELECT 1'
);
PREPARE stmt_sj FROM @sql_sj;
EXECUTE stmt_sj;
DEALLOCATE PREPARE stmt_sj;
