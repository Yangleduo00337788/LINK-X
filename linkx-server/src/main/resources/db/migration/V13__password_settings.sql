-- 作者：yangleduo
-- 密码策略配置（管理端与客户端共用）
SET @has_pwd_min = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'password_min_length'
);
SET @sql = IF(@has_pwd_min = 0,
  'ALTER TABLE `sys_runtime_setting`
     ADD COLUMN `password_min_length` int NOT NULL DEFAULT 8 COMMENT ''密码最小长度'' AFTER `admin_lock_duration_minutes`,
     ADD COLUMN `password_max_length` int NOT NULL DEFAULT 64 COMMENT ''密码最大长度'' AFTER `password_min_length`,
     ADD COLUMN `password_require_upper_lower` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否必须同时包含大小写字母'' AFTER `password_max_length`,
     ADD COLUMN `password_require_digit` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''是否必须包含数字'' AFTER `password_require_upper_lower`,
     ADD COLUMN `password_require_special` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否必须包含特殊字符'' AFTER `password_require_digit`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
