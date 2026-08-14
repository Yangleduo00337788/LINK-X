-- 灵伴：模型是否支持深度思考（管理端保存配置时自动检测写入）
ALTER TABLE sys_runtime_setting
    ADD COLUMN linkmate_reasoning_supported TINYINT(1) DEFAULT NULL COMMENT '当前模型是否支持深度思考' AFTER linkmate_system_prompt;
