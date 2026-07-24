-- 补齐本地/已有库与实体不一致的 IM 字段（可重复执行）
-- 用法: mysql -uroot -p linkx < docs/sql/2026-07-24_im_member_message_columns.sql

SET @db := DATABASE();

-- ---------- helper: add column if missing ----------
-- im_conversation_member
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_conversation_member' AND COLUMN_NAME='last_read_message_id')=0,
  'ALTER TABLE `im_conversation_member` ADD COLUMN `last_read_message_id` bigint DEFAULT NULL COMMENT ''已读到的最后消息ID'' AFTER `remark`',
  'SELECT ''skip last_read_message_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_conversation_member' AND COLUMN_NAME='pinned')=0,
  'ALTER TABLE `im_conversation_member` ADD COLUMN `pinned` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否置顶'' AFTER `last_read_message_id`',
  'SELECT ''skip pinned'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_conversation_member' AND COLUMN_NAME='mute')=0,
  'ALTER TABLE `im_conversation_member` ADD COLUMN `mute` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''免打扰'' AFTER `mute_until`',
  'SELECT ''skip mute'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_conversation_member' AND COLUMN_NAME='announcement_read')=0,
  'ALTER TABLE `im_conversation_member` ADD COLUMN `announcement_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''群公告已读'' AFTER `mute`',
  'SELECT ''skip announcement_read'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_conversation_member' AND COLUMN_NAME='important')=0,
  'ALTER TABLE `im_conversation_member` ADD COLUMN `important` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''重要会话'' AFTER `pinned`',
  'SELECT ''skip important'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- im_message（实体 ImMessage 依赖）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='client_msg_id')=0,
  'ALTER TABLE `im_message` ADD COLUMN `client_msg_id` varchar(128) DEFAULT NULL COMMENT ''客户端幂等ID'' AFTER `file_url`',
  'SELECT ''skip client_msg_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='delivery_status')=0,
  'ALTER TABLE `im_message` ADD COLUMN `delivery_status` varchar(20) DEFAULT ''pending'' COMMENT ''投递状态'' AFTER `client_msg_id`',
  'SELECT ''skip delivery_status'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='read_status')=0,
  'ALTER TABLE `im_message` ADD COLUMN `read_status` tinyint NOT NULL DEFAULT 0 COMMENT ''已读状态'' AFTER `delivery_status`',
  'SELECT ''skip read_status'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='edited')=0,
  'ALTER TABLE `im_message` ADD COLUMN `edited` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否编辑过'' AFTER `voice_duration`',
  'SELECT ''skip edited'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='edited_time')=0,
  'ALTER TABLE `im_message` ADD COLUMN `edited_time` datetime DEFAULT NULL COMMENT ''最后编辑时间'' AFTER `edited`',
  'SELECT ''skip edited_time'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='forward_from_message_id')=0,
  'ALTER TABLE `im_message` ADD COLUMN `forward_from_message_id` bigint DEFAULT NULL COMMENT ''转发来源消息ID'' AFTER `edited_time`',
  'SELECT ''skip forward_from_message_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='forward_from_conversation_id')=0,
  'ALTER TABLE `im_message` ADD COLUMN `forward_from_conversation_id` bigint DEFAULT NULL COMMENT ''转发来源会话ID'' AFTER `forward_from_message_id`',
  'SELECT ''skip forward_from_conversation_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='quote_message_id')=0,
  'ALTER TABLE `im_message` ADD COLUMN `quote_message_id` bigint DEFAULT NULL COMMENT ''引用消息ID'' AFTER `forward_from_conversation_id`',
  'SELECT ''skip quote_message_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='quote_conversation_id')=0,
  'ALTER TABLE `im_message` ADD COLUMN `quote_conversation_id` bigint DEFAULT NULL COMMENT ''引用会话ID'' AFTER `quote_message_id`',
  'SELECT ''skip quote_conversation_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='quote_sender_id')=0,
  'ALTER TABLE `im_message` ADD COLUMN `quote_sender_id` bigint DEFAULT NULL COMMENT ''引用发送者ID'' AFTER `quote_conversation_id`',
  'SELECT ''skip quote_sender_id'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='quote_content')=0,
  'ALTER TABLE `im_message` ADD COLUMN `quote_content` text COMMENT ''引用内容快照'' AFTER `quote_sender_id`',
  'SELECT ''skip quote_content'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='im_message' AND COLUMN_NAME='quote_type')=0,
  'ALTER TABLE `im_message` ADD COLUMN `quote_type` varchar(20) DEFAULT NULL COMMENT ''引用消息类型'' AFTER `quote_content`',
  'SELECT ''skip quote_type'' AS info'
); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
