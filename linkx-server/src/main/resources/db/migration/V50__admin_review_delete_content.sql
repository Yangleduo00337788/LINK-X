-- =============================================================================
-- V50: 审核「独立下架内容」权限
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2213, 'admin:review:delete-content', '下架审核内容', 'button', NULL, '独立删除/撤回违规内容', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2213, NULL),
(1004, 2213, NULL);
