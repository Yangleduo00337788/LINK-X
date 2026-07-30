-- 登录配置：客户端/管理端独立的最大重试与锁定时长；用户临时封禁截止时间
SET @has_client_max = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'client_login_max_attempts'
);
SET @sql = IF(@has_client_max = 0,
  'ALTER TABLE `sys_runtime_setting`
     ADD COLUMN `client_login_max_attempts` int NOT NULL DEFAULT 5 COMMENT ''客户端登录失败最大次数'' AFTER `client_forgot_password_email_enabled`,
     ADD COLUMN `client_lock_duration_minutes` int NOT NULL DEFAULT 10 COMMENT ''客户端自动封禁分钟数'' AFTER `client_login_max_attempts`,
     ADD COLUMN `admin_login_max_attempts` int NOT NULL DEFAULT 5 COMMENT ''管理端登录失败最大次数'' AFTER `admin_captcha_enabled`,
     ADD COLUMN `admin_lock_duration_minutes` int NOT NULL DEFAULT 10 COMMENT ''管理端自动封禁分钟数'' AFTER `admin_login_max_attempts`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_auto_lock = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'auto_locked_until'
);
SET @sql = IF(@has_auto_lock = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `auto_locked_until` datetime NULL COMMENT ''登录失败自动封禁截止时间，到期自动解封；人工禁用则为 NULL'' AFTER `status`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
