-- 生产库增量：通知策略字段
-- MySQL 8：无 IF NOT EXISTS 时请先检查 information_schema 再执行；已存在的列跳过即可。

ALTER TABLE user_preference ADD COLUMN quiet_hours_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE user_preference ADD COLUMN quiet_hours_start VARCHAR(8) DEFAULT '22:00';
ALTER TABLE user_preference ADD COLUMN quiet_hours_end VARCHAR(8) DEFAULT '08:00';
ALTER TABLE user_preference ADD COLUMN notify_chat TINYINT NOT NULL DEFAULT 1;
ALTER TABLE user_preference ADD COLUMN notify_social TINYINT NOT NULL DEFAULT 1;
ALTER TABLE user_preference ADD COLUMN notify_moments TINYINT NOT NULL DEFAULT 1;
ALTER TABLE user_preference ADD COLUMN notify_system TINYINT NOT NULL DEFAULT 1;
