-- 作者：yangleduo
-- =============================================================================
-- V44: IP 限流控制台菜单与权限
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(42, 7, 'rate-limit', 'IP 限流', '/admin/rate-limits', 'views/RateLimitView', 'Speedometer', 'menu', 'admin:rate-limit:list', 4, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 42),
(1005, 42);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2205, 'admin:rate-limit:list', '查看 IP 限流', 'page', '/admin/rate-limits', '限流控制台', 1),
(2206, 'admin:rate-limit:unblock', '解除 IP 限流', 'button', NULL, '清除限流计数', 1),
(2207, 'admin:rate-limit:whitelist', '管理限流白名单', 'button', NULL, 'IP 白名单', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2205, NULL), (1001, 2206, NULL), (1001, 2207, NULL),
(1005, 2205, NULL), (1005, 2206, NULL), (1005, 2207, NULL);
