-- =============================================================================
-- V56: 管理端定时任务配置（可查看/改 cron/立即执行）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_scheduled_task` (
  `task_key`           varchar(64)  NOT NULL COMMENT '任务唯一键',
  `title`              varchar(128) NOT NULL COMMENT '展示名称',
  `description`        varchar(512) DEFAULT NULL COMMENT '说明',
  `schedule_type`      varchar(16)  NOT NULL COMMENT 'cron / fixed_delay',
  `cron_expression`  varchar(64)  DEFAULT NULL COMMENT 'Spring 6 位 cron',
  `fixed_delay_ms`     bigint       DEFAULT NULL COMMENT 'fixedDelay 毫秒',
  `initial_delay_ms`   bigint       DEFAULT NULL COMMENT '首次延迟毫秒',
  `enabled`            tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `shedlock_name`      varchar(128) DEFAULT NULL COMMENT 'ShedLock 锁名',
  `lock_at_most`       varchar(16)  DEFAULT NULL COMMENT '锁最长持有 ISO-8601',
  `lock_at_least`      varchar(16)  DEFAULT NULL COMMENT '锁最短持有 ISO-8601',
  `last_run_at`        datetime     DEFAULT NULL COMMENT '上次执行时间',
  `last_run_status`    varchar(16)  DEFAULT NULL COMMENT 'success/failed/skipped',
  `last_run_message`   varchar(512) DEFAULT NULL COMMENT '上次结果摘要',
  `last_duration_ms`   bigint       DEFAULT NULL COMMENT '上次耗时毫秒',
  `update_by`          bigint       DEFAULT NULL,
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`task_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务配置';

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(47, 0, 'scheduled-tasks', '定时任务', '/admin/scheduled-tasks', 'views/ScheduledTaskListView', 'Timer', 'menu', 'admin:scheduled-task:list', 8, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2224, 'admin:scheduled-task:list', '查看定时任务', 'page', '/admin/scheduled-tasks', '定时任务列表', 1),
(2225, 'admin:scheduled-task:edit', '编辑定时任务', 'button', NULL, '修改 cron/启停', 1),
(2226, 'admin:scheduled-task:run', '立即执行定时任务', 'button', NULL, '手动触发', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2224, NULL), (1001, 2225, NULL), (1001, 2226, NULL),
(1003, 2224, NULL), (1003, 2225, NULL), (1003, 2226, NULL);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 47), (1003, 47);
