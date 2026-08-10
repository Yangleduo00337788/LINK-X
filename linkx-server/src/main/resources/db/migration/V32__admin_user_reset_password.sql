-- 作者：yangleduo
-- =============================================================================
-- V32: 管理端重置用户密码权限
-- 对齐管理端开发文档 §5.2
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2173, 'admin:user:reset-password', '重置用户密码', 'button', NULL, '管理员重置普通用户密码并吊销会话', 1);

-- 系统管理员 / 审核 / 安全可重置；运营与只读不可
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2173, NULL),
(1004, 2173, NULL),
(1005, 2173, NULL);
