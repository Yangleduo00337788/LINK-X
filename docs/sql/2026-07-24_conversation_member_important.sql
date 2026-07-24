-- 重要会话高亮（用户维度，独立于置顶）
-- 执行环境：生产 MySQL / 兼容库

ALTER TABLE im_conversation_member ADD COLUMN important TINYINT NOT NULL DEFAULT 0;
