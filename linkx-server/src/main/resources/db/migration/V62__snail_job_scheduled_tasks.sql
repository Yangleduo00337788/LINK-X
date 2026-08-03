-- =============================================================================
-- V62: 定时任务迁移至 SnailJob，移除自研 sys_scheduled_task
-- =============================================================================

DROP TABLE IF EXISTS `sys_scheduled_task`;

DELETE FROM `sys_permission` WHERE `id` IN (2225, 2226);

UPDATE `sys_permission`
SET `permission_name` = '查看定时任务（SnailJob）', `description` = '只读目录；调度请在 SnailJob 控制台'
WHERE `id` = 2224;

UPDATE `sys_admin_menu`
SET `title` = '定时任务 (SnailJob)', `remark` = '内置任务目录；调度配置见 SnailJob 控制台'
WHERE `id` = 47;
