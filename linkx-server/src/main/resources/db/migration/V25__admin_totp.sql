-- =============================================================================
-- V25: 管理端 TOTP 双因素认证
-- =============================================================================

SET @has_totp = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'totp_enabled'
);
SET @sql = IF(@has_totp = 0,
  'ALTER TABLE `sys_user`
     ADD COLUMN `totp_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''管理端 TOTP 是否已启用'' AFTER `phone`,
     ADD COLUMN `totp_secret` VARCHAR(128) DEFAULT NULL COMMENT ''TOTP Base32 密钥（启用后持久化）'' AFTER `totp_enabled`,
     ADD COLUMN `totp_confirmed_at` DATETIME DEFAULT NULL COMMENT ''TOTP 首次确认时间'' AFTER `totp_secret`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_req = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'admin_totp_required'
);
SET @sql = IF(@has_req = 0,
  'ALTER TABLE `sys_runtime_setting`
     ADD COLUMN `admin_totp_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''管理端是否强制开启 2FA'' AFTER `admin_lock_duration_minutes`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
