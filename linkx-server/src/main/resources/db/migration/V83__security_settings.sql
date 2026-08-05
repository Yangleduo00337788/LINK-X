-- 管理端安全配置：接口加密、禁止前端调试
ALTER TABLE `sys_runtime_setting`
    ADD COLUMN `api_encrypt_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用接口加密' AFTER `review_escalation_interval_hours`,
    ADD COLUMN `disable_frontend_debug` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否禁止前端调试（开发者工具）' AFTER `api_encrypt_enabled`;
