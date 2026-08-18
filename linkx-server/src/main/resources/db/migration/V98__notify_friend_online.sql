-- 好友上线提醒偏好（与聊天/社交/友链/系统通道对齐，支持云端同步）
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_preference'
      AND COLUMN_NAME = 'notify_friend_online'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `user_preference`
        ADD COLUMN `notify_friend_online` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''好友上线提醒'' AFTER `notify_system`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
