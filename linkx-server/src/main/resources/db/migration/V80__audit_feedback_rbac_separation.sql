-- 作者：yangleduo
-- =============================================================================
-- V80: 审核/反馈职责分离 — 举报与内容审核仅 admin + audit_admin；反馈仅运营侧
-- =============================================================================

-- 只读观察员：不应查看反馈/审核
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1006 AND `permission_id` IN (2123, 2124, 2125, 2154, 2129, 2130, 2131, 2155);

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1006 AND `menu_id` IN (10, 101, 13, 14, 43, 44);

-- 审核管理员：不应处理用户反馈（bug/建议类）
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1004 AND `permission_id` IN (2123, 2124, 2214, 2215, 2216, 2217, 2218, 2219, 2222, 2223, 2225, 2280, 2281, 2282, 2283, 2284);

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1004 AND `menu_id` IN (10, 101, 102, 103);

-- 运营管理员：不应查看/处理审核与举报
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1003 AND `permission_id` IN (2129, 2130, 2131, 2136, 2152, 2155, 2213, 2270, 2274, 2275, 2276);

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1003 AND `menu_id` IN (13, 14, 43, 44, 75, 76);

-- 安全管理员：不应查看反馈/审核/审批
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1005 AND `permission_id` IN (2123, 2124, 2129, 2130, 2131, 2155, 2213, 2270, 2274, 2275, 2276);

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1005 AND `menu_id` IN (10, 101, 13, 14, 43, 44, 75, 76);
