-- 收藏空间配额 / 标签库 / 视图偏好

CREATE TABLE IF NOT EXISTS `favorite_storage` (
  `user_id`      bigint NOT NULL COMMENT '用户ID',
  `quota_bytes`  bigint NOT NULL DEFAULT 32212254720 COMMENT '配额，默认30GiB',
  `used_bytes`   bigint NOT NULL DEFAULT 0 COMMENT '已用（可由 file_size 汇总校正）',
  `item_count`   int    NOT NULL DEFAULT 0 COMMENT '收藏条数',
  `version`      int    NOT NULL DEFAULT 0,
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏空间配额';

CREATE TABLE IF NOT EXISTS `favorite_tag` (
  `id`           bigint NOT NULL,
  `user_id`      bigint NOT NULL,
  `name`         varchar(64) NOT NULL,
  `color`        varchar(16) DEFAULT NULL COMMENT '展示色，如 #3b82f6',
  `sort_order`   int NOT NULL DEFAULT 0,
  `preset`       tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=系统预设',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ft_user_name` (`user_id`, `name`, `deleted`),
  KEY `idx_ft_user` (`user_id`, `deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏标签库';

-- 用户偏好：收藏视图与排序（列已存在可忽略报错）
ALTER TABLE `user_preference`
  ADD COLUMN `favorites_view_mode` varchar(16) DEFAULT 'grid' COMMENT 'grid/list' AFTER `moments_background`;

ALTER TABLE `user_preference`
  ADD COLUMN `favorites_sort` varchar(16) DEFAULT 'newest' COMMENT 'newest/oldest/title' AFTER `favorites_view_mode`;
