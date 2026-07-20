-- 新增友链背景图字段
-- 时间: 2026-07-20
ALTER TABLE user_preference
ADD COLUMN `moments_background` varchar(512) DEFAULT NULL COMMENT '友链背景图对象 key' AFTER `notify_tone`;
