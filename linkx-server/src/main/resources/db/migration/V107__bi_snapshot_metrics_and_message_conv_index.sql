-- 群消息按时间范围统计活跃会话：覆盖 conversation_id
SET @idx_im_msg_active_conv := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND INDEX_NAME = 'idx_im_message_active_conv'
);
SET @sql_im_active_conv := IF(
    @idx_im_msg_active_conv = 0,
    'CREATE INDEX `idx_im_message_active_conv` ON `im_message` (`deleted`, `create_time`, `conversation_id`)',
    'SELECT 1'
);
PREPARE stmt_im_active_conv FROM @sql_im_active_conv;
EXECUTE stmt_im_active_conv;
DEALLOCATE PREPARE stmt_im_active_conv;
