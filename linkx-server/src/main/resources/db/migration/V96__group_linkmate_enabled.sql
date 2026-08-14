-- 幂等：列已存在则跳过（避免手动补列后 Flyway 重复执行失败）
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_conversation'
      AND COLUMN_NAME = 'linkmate_enabled'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `im_conversation` ADD COLUMN `linkmate_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''群聊灵伴接入(0关1开)'' AFTER `invite_policy`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
