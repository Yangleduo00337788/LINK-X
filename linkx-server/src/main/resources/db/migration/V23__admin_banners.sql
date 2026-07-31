-- =============================================================================
-- V23: 运营 Banner 表 + 菜单与权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_banner` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `title` VARCHAR(128) NOT NULL COMMENT '标题',
  `image_url` VARCHAR(1024) NOT NULL COMMENT '图片URL',
  `link_url` VARCHAR(1024) DEFAULT NULL COMMENT '点击跳转URL',
  `position` VARCHAR(32) NOT NULL DEFAULT 'home' COMMENT '展位：home/login',
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
  KEY `idx_banner_status` (`status`),
  KEY `idx_banner_position` (`position`),
  KEY `idx_banner_sort` (`sort_order`),
  KEY `idx_banner_window` (`start_at`, `end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运营 Banner';

-- 菜单：Banner 管理（插在公告之后）
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(21, 0, 'banners', 'Banner管理', '/admin/banners', 'views/BannerListView', 'Images', 'menu', 'admin:banner:list', 8, 0, 1, 0, 1, 1);

UPDATE `sys_admin_menu` SET `sort_order` = 9 WHERE `name` = 'settings';
UPDATE `sys_admin_menu` SET `sort_order` = 10 WHERE `name` = 'statistics';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 21);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2161,'admin:banner:list','查看Banner列表','page','/admin/banners','Banner管理',1),
(2162,'admin:banner:view','查看Banner详情','button',NULL,'Banner详情',1),
(2163,'admin:banner:create','新增Banner','button',NULL,'新增Banner',1),
(2164,'admin:banner:edit','编辑Banner','button',NULL,'编辑Banner',1),
(2165,'admin:banner:delete','删除Banner','button',NULL,'删除Banner',1),
(2166,'admin:banner:publish','发布Banner','button',NULL,'发布Banner',1),
(2167,'admin:banner:unpublish','下线Banner','button',NULL,'下线Banner',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2161,NULL),(1001,2162,NULL),(1001,2163,NULL),(1001,2164,NULL),
(1001,2165,NULL),(1001,2166,NULL),(1001,2167,NULL);
