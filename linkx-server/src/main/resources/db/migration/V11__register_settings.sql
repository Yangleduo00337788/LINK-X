-- 注册配置：客户端开放注册、忘记密码邮箱验证
SET @has_register = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'client_register_enabled'
);
SET @sql = IF(@has_register = 0,
  'ALTER TABLE `sys_runtime_setting` ADD COLUMN `client_register_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''客户端是否开放注册'' AFTER `client_captcha_enabled`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_forgot = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'client_forgot_password_email_enabled'
);
SET @sql = IF(@has_forgot = 0,
  'ALTER TABLE `sys_runtime_setting` ADD COLUMN `client_forgot_password_email_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''忘记密码邮箱验证是否启用'' AFTER `client_register_enabled`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
