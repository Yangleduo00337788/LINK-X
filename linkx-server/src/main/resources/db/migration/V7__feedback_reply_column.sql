-- 反馈表增加正式回复字段；从 contact 中的 [admin_reply] 标记迁移历史回复
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_feedback' AND COLUMN_NAME = 'reply') = 0,
  'ALTER TABLE `sys_feedback` ADD COLUMN `reply` text NULL COMMENT ''官方回复内容'' AFTER `status`',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_feedback' AND COLUMN_NAME = 'reply_time') = 0,
  'ALTER TABLE `sys_feedback` ADD COLUMN `reply_time` datetime NULL COMMENT ''回复时间'' AFTER `reply`',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `sys_feedback`
SET
  `reply` = TRIM(SUBSTRING(`contact`, LOCATE('[admin_reply] ', `contact`) + LENGTH('[admin_reply] '))),
  `reply_time` = IFNULL(`create_time`, NOW())
WHERE `reply` IS NULL
  AND `contact` IS NOT NULL
  AND LOCATE('[admin_reply] ', `contact`) > 0;

-- 历史已回复反馈补发官方通知（幂等）
INSERT INTO `message_notification`
(`id`, `user_id`, `sender_id`, `sender_name`, `sender_avatar`, `type`, `related_id`, `content`, `read_status`, `create_time`, `deleted`)
SELECT
    f.`id`,
    f.`user_id`,
    NULL,
    'LinkX官方',
    NULL,
    'feedback_replied',
    f.`id`,
    CONCAT('官方回复：', LEFT(IFNULL(f.`reply`, f.`content`), 120)),
    0,
    IFNULL(f.`reply_time`, f.`create_time`),
    0
FROM `sys_feedback` f
WHERE f.`status` = 'replied'
  AND f.`reply` IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `message_notification` n
      WHERE n.`related_id` = f.`id`
        AND n.`type` = 'feedback_replied'
        AND IFNULL(n.`deleted`, 0) = 0
  );
