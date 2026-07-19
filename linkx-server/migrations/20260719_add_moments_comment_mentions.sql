-- 友链评论 @ 提及：为老库补充 mentions 列（幂等）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'moments_comment' AND COLUMN_NAME = 'mentions') = 0,
    'ALTER TABLE `moments_comment` ADD COLUMN `mentions` TEXT DEFAULT NULL COMMENT ''被@的用户ID列表(JSON 数组)''',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
