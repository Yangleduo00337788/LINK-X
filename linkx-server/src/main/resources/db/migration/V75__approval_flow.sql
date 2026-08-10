-- 作者：yangleduo
-- =============================================================================
-- V75: 复杂审批流（多级审批、会签、抄送）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_approval_flow` (
  `id`           bigint       NOT NULL COMMENT '雪花ID',
  `name`         varchar(64)  NOT NULL COMMENT '流程名称',
  `biz_type`     varchar(32)  NOT NULL DEFAULT 'review' COMMENT '业务类型 review|feedback|generic',
  `description`  varchar(256) NULL COMMENT '说明',
  `steps_json`   json         NOT NULL COMMENT '步骤定义 JSON 数组',
  `enabled`      tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `auto_start`   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '高风险等业务自动发起',
  `priority`     int          NOT NULL DEFAULT 0 COMMENT '优先级，大者优先',
  `created_by`   bigint       NULL,
  `updated_by`   bigint       NULL,
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_approval_flow_biz` (`biz_type`, `enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义';

CREATE TABLE IF NOT EXISTS `sys_approval_instance` (
  `id`             bigint       NOT NULL COMMENT '雪花ID',
  `flow_id`        bigint       NOT NULL COMMENT '流程ID',
  `flow_name`      varchar(64)  NOT NULL COMMENT '流程名称快照',
  `biz_type`       varchar(32)  NOT NULL COMMENT '业务类型',
  `biz_id`         varchar(64)  NOT NULL COMMENT '业务主键',
  `title`          varchar(256) NOT NULL COMMENT '标题',
  `status`         varchar(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected|cancelled',
  `current_step`   int          NOT NULL DEFAULT 0 COMMENT '当前步骤索引',
  `applicant_id`   bigint       NULL COMMENT '发起人',
  `applicant_name` varchar(64)  NULL COMMENT '发起人姓名',
  `finished_at`    datetime     NULL COMMENT '结束时间',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_approval_instance_biz` (`biz_type`, `biz_id`, `status`),
  KEY `idx_approval_instance_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例';

CREATE TABLE IF NOT EXISTS `sys_approval_record` (
  `id`             bigint       NOT NULL COMMENT '雪花ID',
  `instance_id`    bigint       NOT NULL COMMENT '实例ID',
  `step_index`     int          NOT NULL COMMENT '步骤索引',
  `step_name`      varchar(64)  NOT NULL COMMENT '步骤名称',
  `node_type`      varchar(16)  NOT NULL COMMENT 'approve|countersign|cc',
  `assignee_id`    bigint       NOT NULL COMMENT '处理人',
  `assignee_name`  varchar(64)  NULL COMMENT '处理人姓名',
  `status`         varchar(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected|read',
  `comment`        varchar(512) NULL COMMENT '意见',
  `action_time`    datetime     NULL COMMENT '处理时间',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_approval_record_assignee` (`assignee_id`, `status`, `create_time`),
  KEY `idx_approval_record_instance` (`instance_id`, `step_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';

ALTER TABLE `sys_review_task`
  ADD COLUMN `approval_instance_id` bigint NULL COMMENT '关联审批实例' AFTER `assignee_id`;

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2270, 'admin:approval-flow:list', '查看审批流程', 'page', '/admin/approval-flows', '审批流程定义', 1),
(2271, 'admin:approval-flow:create', '新增审批流程', 'button', NULL, '新增流程', 1),
(2272, 'admin:approval-flow:edit', '编辑审批流程', 'button', NULL, '编辑流程', 1),
(2273, 'admin:approval-flow:delete', '删除审批流程', 'button', NULL, '删除流程', 1),
(2274, 'admin:approval:inbox', '审批待办', 'page', '/admin/approval-inbox', '我的审批待办', 1),
(2275, 'admin:approval:action', '审批处理', 'button', NULL, '通过/驳回', 1),
(2276, 'admin:approval:start', '发起审批', 'button', NULL, '手动发起审批', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2270, NULL), (1001, 2271, NULL), (1001, 2272, NULL), (1001, 2273, NULL),
(1001, 2274, NULL), (1001, 2275, NULL), (1001, 2276, NULL),
(1004, 2270, NULL), (1004, 2274, NULL), (1004, 2275, NULL), (1004, 2276, NULL),
(1005, 2270, NULL), (1005, 2274, NULL), (1005, 2275, NULL), (1005, 2276, NULL);

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(75, 13, 'approval-flows', '审批流程', '/admin/approval-flows', 'views/ApprovalFlowListView', 'GitBranch', 'menu', 'admin:approval-flow:list', 3, 0, 1, 0, 1, 1, 0),
(76, 13, 'approval-inbox', '审批待办', '/admin/approval-inbox', 'views/ApprovalInboxView', 'CheckmarkDone', 'menu', 'admin:approval:inbox', 4, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 75), (1001, 76), (1004, 75), (1004, 76), (1005, 75), (1005, 76);
