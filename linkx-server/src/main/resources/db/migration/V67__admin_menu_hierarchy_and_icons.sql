-- =============================================================================
-- V67: 菜单层级整理（父目录 + 子菜单）+ 图标语义化去重
-- =============================================================================

-- 新目录：安全风控(58)、通知公告(55)、运营中心(56)
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(58, 0, 'security-center', '安全风控', '/admin/security', NULL, 'LockClosed', 'dir', NULL, 3, 0, 1, 0, 1, 1),
(55, 0, 'notice-center', '通知公告', '/admin/notice-hub', NULL, 'Mail', 'dir', NULL, 8, 0, 1, 0, 1, 1),
(56, 0, 'ops-center', '运营中心', '/admin/ops', NULL, 'Storefront', 'dir', NULL, 9, 0, 1, 0, 1, 1);

-- 安全风控子菜单
UPDATE `sys_admin_menu` SET `parent_id` = 58, `sort_order` = 1 WHERE `id` = 20;
UPDATE `sys_admin_menu` SET `parent_id` = 58, `sort_order` = 2 WHERE `id` = 22;

-- 通知公告子菜单
UPDATE `sys_admin_menu` SET `parent_id` = 55, `sort_order` = 1 WHERE `id` = 17;
UPDATE `sys_admin_menu` SET `parent_id` = 55, `sort_order` = 2 WHERE `id` = 16;

-- 运营中心子菜单
UPDATE `sys_admin_menu` SET `parent_id` = 56, `sort_order` = 1 WHERE `id` = 21;
UPDATE `sys_admin_menu` SET `parent_id` = 56, `sort_order` = 2 WHERE `id` = 46;
UPDATE `sys_admin_menu` SET `parent_id` = 56, `sort_order` = 3 WHERE `id` = 40;
UPDATE `sys_admin_menu` SET `parent_id` = 56, `sort_order` = 4 WHERE `id` = 41;

-- 审核中心子菜单排序
UPDATE `sys_admin_menu` SET `sort_order` = 1 WHERE `id` = 43;
UPDATE `sys_admin_menu` SET `sort_order` = 2 WHERE `id` = 14;
UPDATE `sys_admin_menu` SET `sort_order` = 3 WHERE `id` = 44;
UPDATE `sys_admin_menu` SET `sort_order` = 4 WHERE `id` = 15;

-- 图标语义化（避免 Shield / Bell / Grid 重复）
UPDATE `sys_admin_menu` SET `icon` = 'Eye' WHERE `id` = 13;
UPDATE `sys_admin_menu` SET `icon` = 'Clipboard' WHERE `id` = 14;
UPDATE `sys_admin_menu` SET `icon` = 'Funnel' WHERE `id` = 15;
UPDATE `sys_admin_menu` SET `icon` = 'Newspaper' WHERE `id` = 16;
UPDATE `sys_admin_menu` SET `icon` = 'Notifications' WHERE `id` = 17;
UPDATE `sys_admin_menu` SET `icon` = 'Images' WHERE `id` = 21;
UPDATE `sys_admin_menu` SET `icon` = 'ReorderFour' WHERE `id` = 46;
UPDATE `sys_admin_menu` SET `icon` = 'Star' WHERE `id` = 40;
UPDATE `sys_admin_menu` SET `icon` = 'Calendar' WHERE `id` = 41;
UPDATE `sys_admin_menu` SET `icon` = 'Pulse' WHERE `id` = 49;
UPDATE `sys_admin_menu` SET `icon` = 'Layers' WHERE `id` = 50;
UPDATE `sys_admin_menu` SET `icon` = 'Server' WHERE `id` = 51;
UPDATE `sys_admin_menu` SET `icon` = 'Analytics' WHERE `id` = 52;
UPDATE `sys_admin_menu` SET `icon` = 'Timer' WHERE `id` = 53;
UPDATE `sys_admin_menu` SET `icon` = 'Code' WHERE `id` = 54;

-- 根菜单排序
UPDATE `sys_admin_menu` SET `sort_order` = 1 WHERE `id` = 1;
UPDATE `sys_admin_menu` SET `sort_order` = 2 WHERE `id` = 2;
UPDATE `sys_admin_menu` SET `sort_order` = 3 WHERE `id` = 58;
UPDATE `sys_admin_menu` SET `sort_order` = 4 WHERE `id` = 3;
UPDATE `sys_admin_menu` SET `sort_order` = 5 WHERE `id` = 7;
UPDATE `sys_admin_menu` SET `sort_order` = 6 WHERE `id` = 13;
UPDATE `sys_admin_menu` SET `sort_order` = 7 WHERE `id` = 10;
UPDATE `sys_admin_menu` SET `sort_order` = 8 WHERE `id` = 55;
UPDATE `sys_admin_menu` SET `sort_order` = 9 WHERE `id` = 56;
UPDATE `sys_admin_menu` SET `sort_order` = 10 WHERE `id` = 49;
UPDATE `sys_admin_menu` SET `sort_order` = 11 WHERE `id` = 11;
UPDATE `sys_admin_menu` SET `sort_order` = 12 WHERE `id` = 18;

-- 隐藏已下线菜单
UPDATE `sys_admin_menu`
SET `hidden` = 1, `status` = 0, `deleted` = 1, `updated_at` = NOW()
WHERE `name` = 'versions' OR `path` = '/admin/versions';
UPDATE `sys_admin_menu` SET `hidden` = 1, `sort_order` = 99 WHERE `id` = 47 AND `name` = 'scheduled-tasks';

-- 角色绑定新目录（父级由子菜单权限自动补齐，此处便于角色配置页展示）
INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 55), (1001, 56), (1001, 58),
(1003, 55), (1003, 56),
(1005, 58);
