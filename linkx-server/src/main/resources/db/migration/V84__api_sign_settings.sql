-- 管理端 API 请求签名开关
ALTER TABLE `sys_runtime_setting`
    ADD COLUMN `api_sign_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用 API 请求签名' AFTER `disable_frontend_debug`;
