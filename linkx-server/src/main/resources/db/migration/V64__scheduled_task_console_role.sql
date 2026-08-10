-- 作者：yangleduo
-- =============================================================================
-- V64: 修复 V63 角色权限绑定（sys_role_permission.id 必填）
-- =============================================================================

INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
VALUES (92227, 1001, 2227, NULL, 0);
