-- 上线 Schema 校验：全部应为 OK（在目标库执行）
-- 用法: mysql -u... -p... linkx < docs/sql/verify_launch_schema.sql

SELECT IF(COUNT(*) = 7, 'OK user_preference notify cols', 'MISSING user_preference notify cols') AS check_result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_preference'
  AND COLUMN_NAME IN (
    'quiet_hours_enabled', 'quiet_hours_start', 'quiet_hours_end',
    'notify_chat', 'notify_social', 'notify_moments', 'notify_system'
  );

SELECT IF(COUNT(*) = 1, 'OK im_conversation_member.important', 'MISSING important') AS check_result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'im_conversation_member'
  AND COLUMN_NAME = 'important';

SELECT IF(COUNT(*) = 2, 'OK im_conversation join/invite cols', 'MISSING join_approval/invite_policy') AS check_result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'im_conversation'
  AND COLUMN_NAME IN ('join_approval', 'invite_policy');

SELECT IF(COUNT(*) = 4, 'OK im_conversation_member read/mute cols', 'MISSING member last_read/pinned/mute/announcement_read') AS check_result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'im_conversation_member'
  AND COLUMN_NAME IN ('last_read_message_id', 'pinned', 'mute', 'announcement_read');

SELECT IF(COUNT(*) = 5, 'OK im_message delivery/quote cols', 'MISSING im_message client_msg/delivery/read/edited/quote') AS check_result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'im_message'
  AND COLUMN_NAME IN ('client_msg_id', 'delivery_status', 'read_status', 'edited', 'quote_message_id');


SELECT IF(COUNT(*) = 1, 'OK im_message_storm_event', 'MISSING im_message_storm_event') AS check_result
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'im_message_storm_event';

SELECT IF(COUNT(*) >= 1, 'OK idx_im_message_conv_id', 'MISSING idx_im_message_conv_id') AS check_result
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'im_message'
  AND INDEX_NAME = 'idx_im_message_conv_id';
