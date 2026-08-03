-- =============================================================================
-- V63: 定时任务页信息架构收敛 — 控制台入口权限、菜单文案
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`)
VALUES (2227, 'admin:scheduled-task:console', '打开 SnailJob 控制台', 'button', NULL, '跳转调度中心进行 cron/启停等写操作', 1);

-- 仅超级管理员可打开 SnailJob 控制台（id 必填，见 V64 修复绑定）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES (1001, 2227, NULL);

UPDATE `sys_admin_menu`
SET `title` = '定时任务', `remark` = '内置任务只读监控；调度写操作见 SnailJob 控制台'
WHERE `id` = 47;

UPDATE `sys_permission`
SET `permission_name` = '查看定时任务', `description` = '只读监控内置任务运行状态与执行日志'
WHERE `id` = 2224;
