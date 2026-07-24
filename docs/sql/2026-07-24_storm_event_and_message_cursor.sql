-- 消息风暴事件表 + 历史消息游标索引
-- 执行环境：生产 MySQL / 兼容库

CREATE TABLE IF NOT EXISTS im_message_storm_event (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT,
  conversation_id BIGINT,
  event_type VARCHAR(32) NOT NULL COMMENT 'user_rate / group_rate',
  message_count INT,
  window_seconds INT,
  member_count INT,
  create_time DATETIME,
  KEY idx_storm_event_user (user_id, create_time),
  KEY idx_storm_event_conv (conversation_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 稳定游标分页：(conversation_id, id)
CREATE INDEX idx_im_message_conv_id ON im_message (conversation_id, id);
