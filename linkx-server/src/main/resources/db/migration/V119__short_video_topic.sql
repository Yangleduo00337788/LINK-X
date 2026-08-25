-- 短视频话题标签

CREATE TABLE IF NOT EXISTS `short_video_topic` (
  `id` bigint NOT NULL COMMENT '雪花 ID',
  `name` varchar(64) NOT NULL COMMENT '话题名（不含#，已规范化）',
  `post_count` int NOT NULL DEFAULT 0 COMMENT '关联作品数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_video_topic_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `short_video_post_topic` (
  `post_id` bigint NOT NULL,
  `topic_id` bigint NOT NULL,
  PRIMARY KEY (`post_id`, `topic_id`),
  KEY `idx_short_video_post_topic_topic` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
