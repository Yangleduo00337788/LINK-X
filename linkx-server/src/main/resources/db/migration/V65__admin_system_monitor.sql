-- =============================================================================
-- V65: 管理端系统监控（运行状态 / 依赖健康 / 全库表体量）
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(49, 0, 'system-monitor', '系统监控', '/admin/system-monitor', 'views/SystemMonitorView', 'Pulse', 'menu', 'admin:system-monitor:view', 9, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2228, 'admin:system-monitor:view', '查看系统监控', 'page', '/admin/system-monitor', '运行状态、依赖健康、数据库表体量', 1);

INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`) VALUES
(92228, 1001, 2228, NULL, 0),
(92229, 1003, 2228, NULL, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 49), (1003, 49);
