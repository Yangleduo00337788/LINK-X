-- =============================================================================
-- V41: 角色独立权限绑定 admin:role:assign-permission
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2204, 'admin:role:assign-permission', '角色分配权限', 'button', NULL, '角色权限点授权', 1);

-- 系统管理员可分配权限
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2204, NULL);
