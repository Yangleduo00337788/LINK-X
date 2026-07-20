-- 添加友链发布的位置、提醒谁看、谁可以看字段
ALTER TABLE moments_post
ADD COLUMN `location` varchar(256) DEFAULT NULL COMMENT '发布位置' AFTER `content`,
ADD COLUMN `at_users` varchar(512) DEFAULT NULL COMMENT '提醒谁看，用户ID列表，逗号分隔' AFTER `location`,
ADD COLUMN `visibility` tinyint NOT NULL DEFAULT 0 COMMENT '可见性：0=公开，1=仅好友，2=私密' AFTER `at_users`;
