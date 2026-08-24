-- 短视频收藏

CREATE TABLE IF NOT EXISTS `short_video_favorite` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '收藏用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_video_favorite_post_user` (`post_id`, `user_id`),
  KEY `idx_short_video_favorite_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频收藏表';
