-- =============================================================================
-- V72: 反馈完整规则引擎（条件树 + 多动作 + 值班表 + 通知）
-- =============================================================================

ALTER TABLE `sys_feedback_dispatch_rule`
  MODIFY COLUMN `assignee_id` bigint NULL COMMENT '固定处理人（assignee_source=fixed 时使用）',
  ADD COLUMN `assignee_source` varchar(16) NOT NULL DEFAULT 'fixed' COMMENT 'fixed|duty|round_robin' AFTER `assignee_id`,
  ADD COLUMN `duty_schedule_id` bigint NULL COMMENT '值班表ID（assignee_source=duty 时使用）' AFTER `assignee_source`,
  ADD COLUMN `condition_json` json NULL COMMENT '扩展条件树，空则回退 feedback_type+keyword' AFTER `keyword`,
  ADD COLUMN `action_type` varchar(32) NOT NULL DEFAULT 'assign' COMMENT 'assign|notify|assign_notify' AFTER `priority`,
  ADD COLUMN `action_config` json NULL COMMENT '轮询池等动作配置' AFTER `action_type`,
  ADD COLUMN `notify_roles` varchar(256) NULL COMMENT '通知角色编码，逗号分隔' AFTER `action_config`,
  ADD COLUMN `notify_channels` varchar(64) NULL COMMENT 'sse,email 等，逗号分隔' AFTER `notify_roles`;

CREATE TABLE IF NOT EXISTS `sys_duty_schedule` (
  `id`          bigint       NOT NULL COMMENT '雪花ID',
  `name`        varchar(64)  NOT NULL COMMENT '值班表名称',
  `description` varchar(256) NULL COMMENT '说明',
  `timezone`    varchar(64)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  `enabled`     tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by`  bigint       NULL,
  `updated_by`  bigint       NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_duty_schedule_enabled` (`enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='值班表';

CREATE TABLE IF NOT EXISTS `sys_duty_schedule_slot` (
  `id`           bigint   NOT NULL COMMENT '雪花ID',
  `schedule_id`  bigint   NOT NULL COMMENT '值班表ID',
  `weekday`      tinyint  NOT NULL COMMENT '1=周一..7=周日',
  `start_time`   time     NOT NULL COMMENT '开始时间',
  `end_time`     time     NOT NULL COMMENT '结束时间',
  `assignee_id`  bigint   NOT NULL COMMENT '值班处理人',
  `sort_order`   int      NOT NULL DEFAULT 0 COMMENT '同时间段排序',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_duty_slot_schedule` (`schedule_id`, `weekday`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='值班时段';

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2220, 'admin:duty-schedule:list', '查看值班表', 'page', '/admin/duty-schedules', '反馈值班表', 1),
(2221, 'admin:duty-schedule:view', '查看值班表详情', 'button', NULL, '值班表详情', 1),
(2222, 'admin:duty-schedule:create', '新增值班表', 'button', NULL, '新增值班表', 1),
(2223, 'admin:duty-schedule:edit', '编辑值班表', 'button', NULL, '编辑值班表', 1),
(2224, 'admin:duty-schedule:delete', '删除值班表', 'button', NULL, '删除值班表', 1),
(2225, 'admin:feedback-dispatch-rule:simulate', '规则模拟', 'button', NULL, '反馈分流规则模拟', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2220, NULL), (1001, 2221, NULL), (1001, 2222, NULL), (1001, 2223, NULL), (1001, 2224, NULL), (1001, 2225, NULL),
(1003, 2220, NULL), (1003, 2221, NULL), (1003, 2222, NULL), (1003, 2223, NULL), (1003, 2224, NULL), (1003, 2225, NULL);

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(103, 10, 'duty-schedules', '值班表', '/admin/duty-schedules', 'views/DutyScheduleListView', 'Time', 'menu', 'admin:duty-schedule:list', 3, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 103), (1003, 103);
