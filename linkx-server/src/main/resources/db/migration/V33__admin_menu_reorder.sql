-- =============================================================================
-- V33: 管理端菜单排序权限
-- 对齐管理端开发文档 §21.4 / §25.2 admin:menu:reorder
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2174, 'admin:menu:reorder', '调整菜单排序', 'button', NULL, '菜单拖拽/上下排序', 1);

-- 系统管理员可排序（运营/审核/安全/只读均不可改菜单）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2174, NULL);
