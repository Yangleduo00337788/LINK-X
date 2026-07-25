-- 会议等候室 + 准入状态；密码列加宽以容纳 BCrypt
-- 可重复执行

SET @has_lobby := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'conference'
     AND COLUMN_NAME = 'lobby_enabled'
);
SET @sql := IF(
  @has_lobby = 0,
  'ALTER TABLE `conference` ADD COLUMN `lobby_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否开启等候室'' AFTER `password`',
  'SELECT ''skip: conference.lobby_enabled'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `conference`
  MODIFY COLUMN `password` varchar(100) DEFAULT NULL COMMENT '会议密码哈希(可选)';

SET @has_admit := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'conference_member'
     AND COLUMN_NAME = 'admit_status'
);
SET @sql2 := IF(
  @has_admit = 0,
  'ALTER TABLE `conference_member` ADD COLUMN `admit_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''0等候 1已准入'' AFTER `left_flag`',
  'SELECT ''skip: conference_member.admit_status'' AS info'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
