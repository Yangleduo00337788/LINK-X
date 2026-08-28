-- 灵伴 Agent 全局开关与群 AI 默认策略
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_runtime_setting'
      AND COLUMN_NAME = 'linkmate_agent_enabled'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `sys_runtime_setting`
        ADD COLUMN `linkmate_agent_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''灵伴 Agent 全局开关(0关1开)'' AFTER `linkmate_realtime_voice`,
        ADD COLUMN `group_linkmate_default_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''新建群默认开启灵伴(0关1开)'' AFTER `linkmate_agent_enabled`,
        ADD COLUMN `group_ai_proactive_default_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''新建群默认开启主动发言(0关1开)'' AFTER `group_linkmate_default_enabled`,
        ADD COLUMN `group_ai_smart_summary_default_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''新建群默认开启智能总结(0关1开)'' AFTER `group_ai_proactive_default_enabled`,
        ADD COLUMN `group_ai_default_interest_topics` varchar(200) DEFAULT NULL COMMENT ''新建群默认关注话题'' AFTER `group_ai_smart_summary_default_enabled`,
        ADD COLUMN `group_ai_default_summary_instruction` varchar(500) DEFAULT NULL COMMENT ''新建群默认总结指令'' AFTER `group_ai_default_interest_topics`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
