-- =============================================================================
-- V69: 反馈中心子菜单、版本发布入口（挂系统配置）、定时任务菜单对齐
-- =============================================================================

-- 反馈：单页改为目录 + 子菜单
UPDATE `sys_admin_menu`
SET `menu_type` = 'dir',
    `name` = 'feedback-center',
    `path` = '/admin/feedback-hub',
    `title` = '反馈中心',
    `permission_code` = NULL,
    `component` = NULL,
    `updated_at` = NOW()
WHERE `id` = 10;

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(101, 10, 'feedback', '反馈列表', '/admin/feedback', 'views/FeedbackListView', 'Chatbox', 'menu', 'admin:feedback:list', 1, 0, 1, 0, 1, 1, 0),
(102, 10, 'feedback-dispatch-rules', '分流规则', '/admin/feedback-dispatch-rules', 'views/FeedbackDispatchRuleListView', 'GitNetwork', 'menu', 'admin:feedback-dispatch-rule:list', 2, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 101), (1001, 102),
(1003, 101), (1003, 102);

-- 版本发布：恢复为系统配置子菜单（运行时 client 配置与发布流分离）
UPDATE `sys_admin_menu`
SET `parent_id` = 11,
    `hidden` = 0,
    `status` = 1,
    `deleted` = 0,
    `sort_order` = 2,
    `menu_type` = 'menu',
    `path` = '/admin/versions',
    `component` = 'views/VersionListView',
    `name` = 'versions',
    `title` = '版本发布',
    `icon` = 'Cube',
    `permission_code` = 'admin:version:list',
    `updated_at` = NOW()
WHERE `name` = 'versions' OR `path` = '/admin/versions' OR `id` = 12;

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(12, 11, 'versions', '版本发布', '/admin/versions', 'views/VersionListView', 'Cube', 'menu', 'admin:version:list', 2, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES (1001, 12);

-- 遗留定时任务菜单指向监控子页
UPDATE `sys_admin_menu`
SET `component` = 'views/monitor/TaskMonitorView',
    `path` = '/admin/system-monitor/tasks',
    `updated_at` = NOW()
WHERE `id` = 47 AND `name` = 'scheduled-tasks';
