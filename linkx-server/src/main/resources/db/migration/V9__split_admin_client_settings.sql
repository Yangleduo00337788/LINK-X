-- 若已执行旧版 V8（仅有 captcha_enabled），拆分为管理端/客户端两套验证码开关
SET @has_old = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'captcha_enabled'
);
SET @has_client = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'client_captcha_enabled'
);
SET @has_admin = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'admin_captcha_enabled'
);

SET @sql = IF(@has_old > 0 AND @has_client = 0,
  'ALTER TABLE `sys_runtime_setting` CHANGE COLUMN `captcha_enabled` `client_captcha_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''客户端登录/注册验证码''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_admin = 0,
  'ALTER TABLE `sys_runtime_setting` ADD COLUMN `admin_captcha_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''管理端登录验证码'' AFTER `id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
