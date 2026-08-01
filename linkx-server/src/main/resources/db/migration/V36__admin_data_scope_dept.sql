-- =============================================================================
-- V36: 细粒度数据权限（全部 / 仅本人 / 本部门及下级）+ 部门管理
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父部门，0=根',
  `name` VARCHAR(64) NOT NULL COMMENT '部门名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` BIGINT DEFAULT NULL,
  `update_by` BIGINT DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sys_dept_parent` (`parent_id`),
  KEY `idx_sys_dept_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门';

INSERT IGNORE INTO `sys_dept` (`id`,`parent_id`,`name`,`sort_order`,`status`) VALUES
(1, 0, '总部', 1, 1),
(2, 1, '运营部', 1, 1),
(3, 1, '审核部', 2, 1),
(4, 1, '安全部', 3, 1);

-- 角色数据范围：1=全部 2=仅本人 3=本部门及下级；默认全部（兼容现网门户角色）
ALTER TABLE `sys_role`
  ADD COLUMN `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据范围：1全部 2本人 3本部门及下级' AFTER `status`;

-- 普通用户角色仅本人，避免默认 ALL 放大可见范围
UPDATE `sys_role` SET `data_scope` = 2 WHERE `role_code` = 'user';

ALTER TABLE `sys_user`
  ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门' AFTER `status`;

CREATE INDEX `idx_sys_user_dept_id` ON `sys_user` (`dept_id`);

-- 部门管理菜单（权限中心下）
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(23, 3, 'dept', '部门管理', '/admin/depts', 'views/DeptListView', 'Business', 'menu', 'admin:dept:list', 4, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 23);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2178,'admin:dept:list','查看部门','page','/admin/depts','部门管理',1),
(2179,'admin:dept:create','新增部门','button',NULL,'新增部门',1),
(2180,'admin:dept:edit','编辑部门','button',NULL,'编辑部门',1),
(2181,'admin:dept:delete','删除部门','button',NULL,'删除部门',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2178,NULL),
(1001,2179,NULL),
(1001,2180,NULL),
(1001,2181,NULL);
