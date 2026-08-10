-- 作者：yangleduo
-- 邮件 SMTP 配置与邮件模板（覆盖 env/yml，管理端可运行时修改）
SET @has_mail_host = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_runtime_setting'
    AND COLUMN_NAME = 'mail_host'
);
SET @sql = IF(@has_mail_host = 0,
  'ALTER TABLE `sys_runtime_setting`
     ADD COLUMN `mail_host` varchar(255) DEFAULT NULL COMMENT ''SMTP 主机'' AFTER `max_upload_bytes`,
     ADD COLUMN `mail_port` int DEFAULT NULL COMMENT ''SMTP 端口'' AFTER `mail_host`,
     ADD COLUMN `mail_username` varchar(255) DEFAULT NULL COMMENT ''SMTP 账号'' AFTER `mail_port`,
     ADD COLUMN `mail_password` varchar(512) DEFAULT NULL COMMENT ''SMTP 授权码'' AFTER `mail_username`,
     ADD COLUMN `mail_from` varchar(255) DEFAULT NULL COMMENT ''发件人地址'' AFTER `mail_password`,
     ADD COLUMN `mail_from_name` varchar(128) DEFAULT NULL COMMENT ''发件人显示名'' AFTER `mail_from`,
     ADD COLUMN `mail_start_tls` tinyint(1) DEFAULT NULL COMMENT ''是否启用 STARTTLS'' AFTER `mail_from_name`,
     ADD COLUMN `mail_ssl` tinyint(1) DEFAULT NULL COMMENT ''是否启用 SSL'' AFTER `mail_start_tls`,
     ADD COLUMN `mail_code_expire_minutes` int DEFAULT NULL COMMENT ''验证码有效分钟'' AFTER `mail_ssl`,
     ADD COLUMN `mail_tpl_register_subject` varchar(255) DEFAULT NULL COMMENT ''注册验证码邮件主题'' AFTER `mail_code_expire_minutes`,
     ADD COLUMN `mail_tpl_register_html` mediumtext DEFAULT NULL COMMENT ''注册验证码邮件 HTML'' AFTER `mail_tpl_register_subject`,
     ADD COLUMN `mail_tpl_reset_subject` varchar(255) DEFAULT NULL COMMENT ''重置密码邮件主题'' AFTER `mail_tpl_register_html`,
     ADD COLUMN `mail_tpl_reset_html` mediumtext DEFAULT NULL COMMENT ''重置密码邮件 HTML'' AFTER `mail_tpl_reset_subject`,
     ADD COLUMN `mail_tpl_welcome_subject` varchar(255) DEFAULT NULL COMMENT ''欢迎邮件主题'' AFTER `mail_tpl_reset_html`,
     ADD COLUMN `mail_tpl_welcome_html` mediumtext DEFAULT NULL COMMENT ''欢迎邮件 HTML'' AFTER `mail_tpl_welcome_subject`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
