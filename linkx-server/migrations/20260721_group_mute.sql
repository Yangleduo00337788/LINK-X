-- 群禁言：全体禁言 / 定时全体禁言 / 成员禁言

-- 1. 会话级全体禁言
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_conversation' AND COLUMN_NAME = 'mute_all') = 0,
    'ALTER TABLE `im_conversation` ADD COLUMN `mute_all` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''全体禁言(0关1开)'' AFTER `owner_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_conversation' AND COLUMN_NAME = 'mute_all_start') = 0,
    'ALTER TABLE `im_conversation` ADD COLUMN `mute_all_start` datetime DEFAULT NULL COMMENT ''定时全体禁言开始时间'' AFTER `mute_all`',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_conversation' AND COLUMN_NAME = 'mute_all_end') = 0,
    'ALTER TABLE `im_conversation` ADD COLUMN `mute_all_end` datetime DEFAULT NULL COMMENT ''定时全体禁言结束时间'' AFTER `mute_all_start`',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 成员级禁言
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_conversation_member' AND COLUMN_NAME = 'muted') = 0,
    'ALTER TABLE `im_conversation_member` ADD COLUMN `muted` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否禁言(0否1是)'' AFTER `remark`',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_conversation_member' AND COLUMN_NAME = 'mute_until') = 0,
    'ALTER TABLE `im_conversation_member` ADD COLUMN `mute_until` datetime DEFAULT NULL COMMENT ''成员禁言截止时间(空=手动解除)'' AFTER `muted`',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
