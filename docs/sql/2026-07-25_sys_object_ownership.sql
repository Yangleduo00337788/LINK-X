-- MinIO object key 属主登记（持久化；Redis 作缓存，flush 后可回填）
CREATE TABLE IF NOT EXISTS `sys_object_ownership` (
  `object_key` varchar(512) NOT NULL COMMENT 'MinIO object key',
  `user_id` bigint NOT NULL COMMENT '属主用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '认领时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`object_key`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象属主登记表';
