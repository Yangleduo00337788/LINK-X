-- 作者：yangleduo
-- =============================================================================
-- V70: 系统配置目录化（配置页 + 版本发布并列）、反馈中心文案 name 对齐
-- =============================================================================

-- 系统配置：目录 + 「配置中心」子菜单（避免仅有 versions 时无法进入 SettingView）
UPDATE `sys_admin_menu`
SET `menu_type` = 'dir',
    `name` = 'settings-center',
    `path` = '/admin/settings-hub',
    `title` = '系统配置',
    `permission_code` = NULL,
    `component` = NULL,
    `updated_at` = NOW()
WHERE `id` = 11;

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(111, 11, 'settings', '配置中心', '/admin/settings', 'views/SettingView', 'Settings', 'menu', 'admin:setting:view', 1, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES (1001, 111);

UPDATE `sys_admin_menu`
SET `sort_order` = 2,
    `title` = '版本发布',
    `updated_at` = NOW()
WHERE `id` = 12;

-- 反馈列表子菜单标题与父级区分
UPDATE `sys_admin_menu`
SET `title` = '反馈列表',
    `updated_at` = NOW()
WHERE `id` = 101;

UPDATE `sys_admin_menu`
SET `title` = '分流规则',
    `updated_at` = NOW()
WHERE `id` = 102;
