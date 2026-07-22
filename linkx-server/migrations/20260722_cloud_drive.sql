-- 个人网盘：文件夹 / 文件 / 标签 / 分享 / 配额 / 动态

CREATE TABLE IF NOT EXISTS `user_storage` (
  `user_id`      bigint NOT NULL COMMENT '用户ID',
  `quota_bytes`  bigint NOT NULL DEFAULT 21474836480 COMMENT '配额，默认20GiB',
  `used_bytes`   bigint NOT NULL DEFAULT 0 COMMENT '已用',
  `file_count`   int    NOT NULL DEFAULT 0,
  `version`      int    NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户网盘配额';

CREATE TABLE IF NOT EXISTS `cloud_folder` (
  `id`           bigint NOT NULL,
  `user_id`      bigint NOT NULL,
  `parent_id`    bigint DEFAULT NULL COMMENT 'NULL=根目录',
  `name`         varchar(255) NOT NULL,
  `path`         varchar(1024) NOT NULL DEFAULT '/' COMMENT '物化路径',
  `sort_order`   int NOT NULL DEFAULT 0,
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cf_user_parent` (`user_id`, `parent_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人网盘文件夹';

CREATE TABLE IF NOT EXISTS `cloud_file` (
  `id`             bigint NOT NULL,
  `user_id`        bigint NOT NULL,
  `folder_id`      bigint DEFAULT NULL COMMENT 'NULL=根目录',
  `name`           varchar(255) NOT NULL COMMENT '展示名',
  `file_name`      varchar(255) NOT NULL COMMENT '原始文件名',
  `file_size`      bigint NOT NULL DEFAULT 0,
  `file_key`       varchar(500) NOT NULL,
  `content_type`   varchar(128) DEFAULT NULL,
  `ext`            varchar(32) DEFAULT NULL,
  `category`       varchar(20) NOT NULL DEFAULT 'other',
  `description`    varchar(1000) DEFAULT NULL,
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cfile_user_folder` (`user_id`, `folder_id`, `deleted`, `create_time`),
  KEY `idx_cfile_category` (`user_id`, `category`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人网盘文件';

CREATE TABLE IF NOT EXISTS `cloud_file_tag` (
  `id`          bigint NOT NULL,
  `user_id`     bigint NOT NULL,
  `file_id`     bigint NOT NULL,
  `tag_name`    varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cft_file_tag` (`file_id`, `tag_name`),
  KEY `idx_cft_user_tag` (`user_id`, `tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘文件标签';

CREATE TABLE IF NOT EXISTS `cloud_share` (
  `id`              bigint NOT NULL,
  `user_id`         bigint NOT NULL,
  `share_type`      varchar(16) NOT NULL COMMENT 'file / folder',
  `target_id`       bigint NOT NULL,
  `token`           varchar(64) NOT NULL,
  `password_hash`   varchar(255) DEFAULT NULL,
  `expire_at`       datetime DEFAULT NULL,
  `max_downloads`   int DEFAULT NULL,
  `download_count`  int NOT NULL DEFAULT 0,
  `status`          tinyint NOT NULL DEFAULT 1 COMMENT '1有效 0关闭',
  `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cs_token` (`token`),
  KEY `idx_cs_user_target` (`user_id`, `share_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘分享';

CREATE TABLE IF NOT EXISTS `cloud_activity` (
  `id`           bigint NOT NULL,
  `user_id`      bigint NOT NULL,
  `target_type`  varchar(16) NOT NULL COMMENT 'file / folder',
  `target_id`    bigint NOT NULL,
  `target_name`  varchar(255) DEFAULT NULL,
  `action`       varchar(32) NOT NULL COMMENT 'upload/create/rename/move/delete/share/tag/download/expand',
  `detail`       varchar(500) DEFAULT NULL,
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ca_user_target` (`user_id`, `target_type`, `target_id`, `create_time`),
  KEY `idx_ca_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘动态';
