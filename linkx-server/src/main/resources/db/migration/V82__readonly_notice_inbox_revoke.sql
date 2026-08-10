-- 作者：yangleduo
-- =============================================================================
-- V82: 只读观察员不应持有通知中心写权限（V31 误授 notice:inbox）
-- =============================================================================

DELETE FROM `sys_role_permission`
WHERE `role_id` = 1006 AND `permission_id` = 2143;

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1006 AND `menu_id` = 17;
