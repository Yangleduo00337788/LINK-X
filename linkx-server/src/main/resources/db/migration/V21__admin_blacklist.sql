-- =============================================================================
-- V21: 管理端平台黑名单表 + 菜单/权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_admin_blacklist` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` BIGINT NOT NULL COMMENT '被封禁用户ID',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '用户名快照',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称快照',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '封禁原因',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active/released',
  `created_by` BIGINT DEFAULT NULL COMMENT '封禁操作人',
  `released_by` BIGINT DEFAULT NULL COMMENT '解封操作人',
  `released_at` DATETIME DEFAULT NULL COMMENT '解封时间',
  `release_reason` VARCHAR(255) DEFAULT NULL COMMENT '解封原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '封禁时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_bl_user_id` (`user_id`),
  KEY `idx_admin_bl_status` (`status`),
  KEY `idx_admin_bl_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端平台黑名单';

-- 回填：当前人工禁用用户（排除登录失败自动封禁）
INSERT INTO `sys_admin_blacklist`
(`id`, `user_id`, `username`, `nickname`, `reason`, `status`, `created_by`, `create_time`, `update_time`)
SELECT
  u.`id`,
  u.`id`,
  u.`username`,
  u.`nickname`,
  '历史封禁回填',
  'active',
  u.`update_by`,
  COALESCE(u.`update_time`, u.`create_time`, NOW()),
  NOW()
FROM `sys_user` u
WHERE u.`status` = 0
  AND u.`deleted` = 0
  AND u.`auto_locked_until` IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_admin_blacklist` b
    WHERE b.`user_id` = u.`id` AND b.`status` = 'active'
  );

-- 菜单：黑名单管理（紧挨用户管理；同 sort_order 时按 id，user 在前）
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(20, 0, 'blacklist', '黑名单管理', '/admin/blacklist', 'views/BlacklistView', 'Ban', 'menu', 'admin:blacklist:list', 2, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 20);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2149,'admin:blacklist:list','查看黑名单','page','/admin/blacklist','黑名单列表',1),
(2150,'admin:blacklist:add','加入黑名单','button',NULL,'加入黑名单',1),
(2151,'admin:blacklist:remove','移出黑名单','button',NULL,'移出黑名单',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2149,NULL),
(1001,2150,NULL),
(1001,2151,NULL);
