-- 作者：yangleduo
-- =============================================================================
-- V16: 管理端公告表 + 菜单与权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_admin_notice` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `title` VARCHAR(128) NOT NULL COMMENT '公告标题',
  `content` TEXT NOT NULL COMMENT '公告正文',
  `status` VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/unpublished',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `published_by` BIGINT DEFAULT NULL COMMENT '发布人',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_notice_status` (`status`),
  KEY `idx_notice_create_time` (`create_time`),
  KEY `idx_notice_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端系统公告';

-- 菜单：公告管理（放在反馈与系统配置之间）
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(16, 0, 'notices', '公告管理', '/admin/notices', 'views/NoticeView', 'Bell', 'menu', 'admin:notice:list', 7, 0, 1, 0, 1, 1);

UPDATE `sys_admin_menu` SET `sort_order` = 8 WHERE `name` = 'settings';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 16);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2136,'admin:notice:list','查看公告列表','page','/admin/notices','公告管理',1),
(2137,'admin:notice:view','查看公告详情','button',NULL,'公告详情',1),
(2138,'admin:notice:create','新增公告','button',NULL,'新增公告',1),
(2139,'admin:notice:edit','编辑公告','button',NULL,'编辑公告',1),
(2140,'admin:notice:delete','删除公告','button',NULL,'删除公告',1),
(2141,'admin:notice:publish','发布公告','button',NULL,'发布公告',1),
(2142,'admin:notice:unpublish','下线公告','button',NULL,'下线公告',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2136,NULL),(1001,2137,NULL),(1001,2138,NULL),(1001,2139,NULL),
(1001,2140,NULL),(1001,2141,NULL),(1001,2142,NULL);
