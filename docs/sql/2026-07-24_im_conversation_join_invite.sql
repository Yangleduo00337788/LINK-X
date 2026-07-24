-- 为已有库补齐群会话入群审批 / 邀请策略字段（实体 ImConversation 已依赖）
-- 执行：mysql -uroot -p linkx < docs/sql/2026-07-24_im_conversation_join_invite.sql
-- 说明：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，故用 information_schema 判断后动态执行

SET @db := DATABASE();

SET @has_join := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'im_conversation' AND COLUMN_NAME = 'join_approval'
);
SET @sql_join := IF(
  @has_join = 0,
  'ALTER TABLE `im_conversation` ADD COLUMN `join_approval` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''入群审批(0不需要1需要)'' AFTER `mute_all_end`',
  'SELECT ''skip join_approval'' AS info'
);
PREPARE stmt FROM @sql_join; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_invite := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'im_conversation' AND COLUMN_NAME = 'invite_policy'
);
SET @sql_invite := IF(
  @has_invite = 0,
  'ALTER TABLE `im_conversation` ADD COLUMN `invite_policy` varchar(20) DEFAULT ''anyMember'' COMMENT ''邀请策略(ownerApprove/anyMember)'' AFTER `join_approval`',
  'SELECT ''skip invite_policy'' AS info'
);
PREPARE stmt FROM @sql_invite; EXECUTE stmt; DEALLOCATE PREPARE stmt;
