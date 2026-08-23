-- 短视频模块 Phase 1

CREATE TABLE IF NOT EXISTS `short_video_post` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '发布者用户ID',
  `description` text COMMENT '作品描述',
  `description_enc_version` tinyint NOT NULL DEFAULT 0 COMMENT '0=明文 1=lxenc:v1',
  `search_text` varchar(512) DEFAULT NULL COMMENT '搜索摘要(明文)',
  `video_key` varchar(500) NOT NULL COMMENT '视频 object key',
  `cover_key` varchar(500) DEFAULT NULL COMMENT '封面 object key',
  `duration_ms` int DEFAULT NULL COMMENT '视频时长(毫秒)',
  `visibility` int NOT NULL DEFAULT 0 COMMENT '0=公开 1=仅好友 2=私密',
  `play_count` bigint NOT NULL DEFAULT 0 COMMENT '播放次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_short_video_user_time` (`user_id`, `create_time`),
  KEY `idx_short_video_desc_enc` (`description_enc_version`, `id`),
  FULLTEXT KEY `ft_short_video_search` (`search_text`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频作品表';

CREATE TABLE IF NOT EXISTS `short_video_like` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '点赞用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_video_like_post_user` (`post_id`, `user_id`),
  KEY `idx_short_video_like_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频点赞表';

CREATE TABLE IF NOT EXISTS `short_video_comment` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '评论用户ID',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `content_enc_version` tinyint NOT NULL DEFAULT 0 COMMENT '0=明文 1=lxenc:v1',
  `parent_id` bigint DEFAULT NULL COMMENT '父评论ID',
  `mentions` text DEFAULT NULL COMMENT '被@用户ID JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_short_video_comment_post_time` (`post_id`, `create_time`),
  KEY `idx_short_video_comment_enc` (`content_enc_version`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频评论表';

CREATE TABLE IF NOT EXISTS `short_video_follow` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `follower_id` bigint NOT NULL COMMENT '关注者用户ID',
  `followee_id` bigint NOT NULL COMMENT '被关注创作者ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_video_follow_pair` (`follower_id`, `followee_id`),
  KEY `idx_short_video_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短视频关注表';
