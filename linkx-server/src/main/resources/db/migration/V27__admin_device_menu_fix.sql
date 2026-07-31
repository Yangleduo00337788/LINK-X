-- =============================================================================
-- V27: 补齐设备管理菜单（V26 误用 id=21，与 V23 Banner 冲突被 IGNORE）
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(22, 0, 'devices', '设备管理', '/admin/devices', 'views/DeviceListView', 'Phone', 'menu', 'admin:device:list', 2, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 22);
