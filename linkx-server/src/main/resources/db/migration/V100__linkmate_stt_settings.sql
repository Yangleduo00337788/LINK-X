-- 灵伴语音转写（STT）独立配置：可与聊天 LLM（如 DeepSeek）分开
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_runtime_setting'
      AND COLUMN_NAME = 'linkmate_stt_api_key'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `sys_runtime_setting`
        ADD COLUMN `linkmate_stt_api_key` varchar(512) DEFAULT NULL COMMENT ''语音转写 API Key'' AFTER `linkmate_reasoning_supported`,
        ADD COLUMN `linkmate_stt_base_url` varchar(512) DEFAULT NULL COMMENT ''语音转写 API 基址'' AFTER `linkmate_stt_api_key`,
        ADD COLUMN `linkmate_stt_model` varchar(128) DEFAULT NULL COMMENT ''语音转写模型'' AFTER `linkmate_stt_base_url`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
