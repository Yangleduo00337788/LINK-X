-- 作者：yangleduo
-- 客户端 / 管理端验证码形态：image（图形字符）| slider（滑块拼图）
SET @has_client_captcha_type = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'client_captcha_type'
);
SET @sql = IF(@has_client_captcha_type = 0,
  'ALTER TABLE `sys_runtime_setting`
     ADD COLUMN `client_captcha_type` varchar(16) NOT NULL DEFAULT ''image'' COMMENT ''客户端验证码类型：image|slider'' AFTER `client_captcha_enabled`,
     ADD COLUMN `admin_captcha_type` varchar(16) NOT NULL DEFAULT ''image'' COMMENT ''管理端验证码类型：image|slider'' AFTER `admin_captcha_enabled`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
