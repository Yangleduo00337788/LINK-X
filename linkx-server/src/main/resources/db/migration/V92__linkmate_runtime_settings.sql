-- 灵伴（LinkMate）运行时配置：管理端可热更新，覆盖 env 默认值
ALTER TABLE sys_runtime_setting
    ADD COLUMN linkmate_enabled TINYINT(1) DEFAULT NULL COMMENT '是否启用灵伴' AFTER mail_tpl_welcome_html,
    ADD COLUMN linkmate_api_key VARCHAR(512) DEFAULT NULL COMMENT 'OpenAI 兼容 API Key' AFTER linkmate_enabled,
    ADD COLUMN linkmate_base_url VARCHAR(512) DEFAULT NULL COMMENT 'API 基址' AFTER linkmate_api_key,
    ADD COLUMN linkmate_model VARCHAR(128) DEFAULT NULL COMMENT '模型名称' AFTER linkmate_base_url,
    ADD COLUMN linkmate_max_tokens INT DEFAULT NULL COMMENT '单次最大生成 token' AFTER linkmate_model,
    ADD COLUMN linkmate_temperature DECIMAL(4, 2) DEFAULT NULL COMMENT '采样温度 0~2' AFTER linkmate_max_tokens,
    ADD COLUMN linkmate_daily_token_limit INT DEFAULT NULL COMMENT '单用户每日 token 估算上限' AFTER linkmate_temperature,
    ADD COLUMN linkmate_system_prompt TEXT DEFAULT NULL COMMENT '系统提示词' AFTER linkmate_daily_token_limit;
