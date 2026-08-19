-- 灵伴 Realtime 语音通话独立配置（OpenAI Realtime；可与聊天 LLM 分开）
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_runtime_setting'
      AND COLUMN_NAME = 'linkmate_realtime_api_key'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `sys_runtime_setting`
        ADD COLUMN `linkmate_realtime_api_key` varchar(512) DEFAULT NULL COMMENT ''Realtime API Key'' AFTER `linkmate_stt_model`,
        ADD COLUMN `linkmate_realtime_base_url` varchar(512) DEFAULT NULL COMMENT ''Realtime API 基址'' AFTER `linkmate_realtime_api_key`,
        ADD COLUMN `linkmate_realtime_model` varchar(128) DEFAULT NULL COMMENT ''Realtime 模型'' AFTER `linkmate_realtime_base_url`,
        ADD COLUMN `linkmate_realtime_voice` varchar(64) DEFAULT NULL COMMENT ''Realtime 音色'' AFTER `linkmate_realtime_model`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
