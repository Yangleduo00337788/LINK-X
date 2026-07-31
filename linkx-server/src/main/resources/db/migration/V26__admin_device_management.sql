-- =============================================================================
-- V26: 管理端设备管理菜单与权限
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(21, 0, 'devices', '设备管理', '/admin/devices', 'views/DeviceListView', 'Phone', 'menu', 'admin:device:list', 2, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 21);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2171,'admin:device:list','查看设备列表','page','/admin/devices','设备会话列表',1),
(2172,'admin:device:kick','强制设备下线','button',NULL,'踢设备下线',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2171,NULL),
(1001,2172,NULL);
