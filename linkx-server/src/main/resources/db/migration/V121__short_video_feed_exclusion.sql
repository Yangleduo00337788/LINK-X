-- 短视频推荐流：不感兴趣 / 屏蔽作者

CREATE TABLE IF NOT EXISTS `short_video_not_interested` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '操作用户ID',
  `post_id` bigint NOT NULL COMMENT '作品ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标记时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_video_ni_user_post` (`user_id`, `post_id`),
  KEY `idx_short_video_ni_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频不感兴趣';

CREATE TABLE IF NOT EXISTS `short_video_author_block` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '操作用户ID',
  `author_id` bigint NOT NULL COMMENT '被屏蔽作者ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '屏蔽时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_video_block_user_author` (`user_id`, `author_id`),
  KEY `idx_short_video_block_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频屏蔽作者';
