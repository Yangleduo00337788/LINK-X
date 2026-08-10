-- 作者：yangleduo
-- =============================================================================
-- V40: 运营推荐位 + 活动表 + 菜单与权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_ops_recommend` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `slot_code` VARCHAR(64) NOT NULL COMMENT '推荐位：discover/chat_sidebar/moments',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '标题',
  `subtitle` VARCHAR(255) DEFAULT NULL COMMENT '副标题',
  `image_url` VARCHAR(1024) NOT NULL COMMENT '图片URL或对象key',
  `link_url` VARCHAR(1024) DEFAULT NULL COMMENT '点击跳转URL',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
  `status` VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/unpublished',
  `start_at` DATETIME DEFAULT NULL COMMENT '生效开始时间',
  `end_at` DATETIME DEFAULT NULL COMMENT '生效结束时间',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `published_by` BIGINT DEFAULT NULL COMMENT '发布人',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_ops_recommend_status` (`status`),
  KEY `idx_ops_recommend_slot` (`slot_code`),
  KEY `idx_ops_recommend_sort` (`sort_order`),
  KEY `idx_ops_recommend_window` (`start_at`, `end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运营推荐位';

CREATE TABLE IF NOT EXISTS `sys_ops_activity` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '标题',
  `cover_url` VARCHAR(1024) NOT NULL COMMENT '封面URL或对象key',
  `link_url` VARCHAR(1024) DEFAULT NULL COMMENT '点击跳转URL',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '活动描述',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
  `status` VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/unpublished',
  `start_at` DATETIME DEFAULT NULL COMMENT '生效开始时间',
  `end_at` DATETIME DEFAULT NULL COMMENT '生效结束时间',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `published_by` BIGINT DEFAULT NULL COMMENT '发布人',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_ops_activity_status` (`status`),
  KEY `idx_ops_activity_sort` (`sort_order`),
  KEY `idx_ops_activity_window` (`start_at`, `end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运营活动';

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(40, 0, 'recommends', '推荐位管理', '/admin/recommends', 'views/RecommendListView', 'Star', 'menu', 'admin:recommend:list', 11, 0, 1, 0, 1, 1),
(41, 0, 'activities', '活动管理', '/admin/activities', 'views/ActivityListView', 'Calendar', 'menu', 'admin:activity:list', 12, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 40), (1001, 41),
(1003, 40), (1003, 41);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2190,'admin:recommend:list','查看推荐位列表','page','/admin/recommends','推荐位管理',1),
(2191,'admin:recommend:view','查看推荐位详情','button',NULL,'推荐位详情',1),
(2192,'admin:recommend:create','新增推荐位','button',NULL,'新增推荐位',1),
(2193,'admin:recommend:edit','编辑推荐位','button',NULL,'编辑推荐位',1),
(2194,'admin:recommend:delete','删除推荐位','button',NULL,'删除推荐位',1),
(2195,'admin:recommend:publish','发布推荐位','button',NULL,'发布推荐位',1),
(2196,'admin:recommend:unpublish','下线推荐位','button',NULL,'下线推荐位',1),
(2197,'admin:activity:list','查看活动列表','page','/admin/activities','活动管理',1),
(2198,'admin:activity:view','查看活动详情','button',NULL,'活动详情',1),
(2199,'admin:activity:create','新增活动','button',NULL,'新增活动',1),
(2200,'admin:activity:edit','编辑活动','button',NULL,'编辑活动',1),
(2201,'admin:activity:delete','删除活动','button',NULL,'删除活动',1),
(2202,'admin:activity:publish','发布活动','button',NULL,'发布活动',1),
(2203,'admin:activity:unpublish','下线活动','button',NULL,'下线活动',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2190,NULL),(1001,2191,NULL),(1001,2192,NULL),(1001,2193,NULL),
(1001,2194,NULL),(1001,2195,NULL),(1001,2196,NULL),
(1001,2197,NULL),(1001,2198,NULL),(1001,2199,NULL),(1001,2200,NULL),
(1001,2201,NULL),(1001,2202,NULL),(1001,2203,NULL),
(1003,2190,NULL),(1003,2191,NULL),(1003,2192,NULL),(1003,2193,NULL),
(1003,2194,NULL),(1003,2195,NULL),(1003,2196,NULL),
(1003,2197,NULL),(1003,2198,NULL),(1003,2199,NULL),(1003,2200,NULL),
(1003,2201,NULL),(1003,2202,NULL),(1003,2203,NULL);
