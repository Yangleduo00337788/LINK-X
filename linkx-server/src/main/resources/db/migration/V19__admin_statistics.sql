-- 作者：yangleduo
-- =============================================================================
-- V19: 统计分析菜单与权限
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(18, 0, 'statistics', '统计分析', '/admin/statistics', 'views/StatisticsView', 'Chart', 'menu', 'admin:statistics:view', 9, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 18);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2144,'admin:statistics:view','查看统计分析','page','/admin/statistics','统计中心',1),
(2145,'admin:statistics:export','导出统计数据','button',NULL,'统计导出',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2144,NULL),
(1001,2145,NULL);
