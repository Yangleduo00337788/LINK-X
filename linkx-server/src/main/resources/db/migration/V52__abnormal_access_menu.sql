-- 作者：yangleduo
-- =============================================================================
-- V52: 异常访问记录专页（统一登录失败/限流命中/访问风险事件）
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(45, 7, 'abnormal-access', '异常访问', '/admin/abnormal-access', 'views/AbnormalAccessView', 'AlertCircle', 'menu', 'admin:abnormal-access:list', 5, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2220, 'admin:abnormal-access:list', '查看异常访问', 'page', '/admin/abnormal-access', '统一异常访问记录', 1),
(2221, 'admin:abnormal-access:export', '导出异常访问', 'button', NULL, '异常访问记录导出', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2220, NULL), (1001, 2221, NULL),
(1004, 2220, NULL), (1004, 2221, NULL),
(1005, 2220, NULL), (1005, 2221, NULL);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 45), (1004, 45), (1005, 45);
