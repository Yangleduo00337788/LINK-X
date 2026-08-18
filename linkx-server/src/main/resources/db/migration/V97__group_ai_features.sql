-- 群聊 AI 主动发言与智能总结配置
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_conversation'
      AND COLUMN_NAME = 'group_ai_proactive_enabled'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `im_conversation`
        ADD COLUMN `group_ai_proactive_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''群聊主动发言(0关1开)'' AFTER `linkmate_enabled`,
        ADD COLUMN `group_ai_interest_topics` varchar(200) DEFAULT NULL COMMENT ''主动发言关注话题'' AFTER `group_ai_proactive_enabled`,
        ADD COLUMN `group_ai_smart_summary_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''群聊智能总结(0关1开)'' AFTER `group_ai_interest_topics`,
        ADD COLUMN `group_ai_summary_instruction` varchar(500) DEFAULT NULL COMMENT ''智能总结指令'' AFTER `group_ai_smart_summary_enabled`,
        ADD COLUMN `group_ai_last_proactive_at` datetime DEFAULT NULL COMMENT ''上次主动发言时间'' AFTER `group_ai_summary_instruction`,
        ADD COLUMN `group_ai_last_summary_at` datetime DEFAULT NULL COMMENT ''上次智能总结时间'' AFTER `group_ai_last_proactive_at`,
        ADD COLUMN `group_ai_last_summary_msg_id` bigint DEFAULT NULL COMMENT ''上次总结覆盖到的消息ID'' AFTER `group_ai_last_summary_at`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
