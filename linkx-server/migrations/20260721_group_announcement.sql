-- 群公告多条 + 置顶

CREATE TABLE IF NOT EXISTS `group_announcement` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` bigint NOT NULL COMMENT '群会话ID',
  `content` text NOT NULL COMMENT '公告内容',
  `publisher_id` bigint NOT NULL COMMENT '发布者用户ID',
  `pinned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否置顶(0/1)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_conv_pinned_time` (`conversation_id`, `pinned`, `create_time`),
  KEY `idx_publisher` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群公告表';
