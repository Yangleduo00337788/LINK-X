-- 作者：yangleduo
-- =============================================================================
-- V51: 反馈指派 + 自动分流规则（轻量规则引擎）
-- =============================================================================

ALTER TABLE `sys_feedback`
  ADD COLUMN `assignee_id` bigint NULL COMMENT '处理人用户ID' AFTER `reply_time`,
  ADD COLUMN `assigned_at` datetime NULL COMMENT '指派时间' AFTER `assignee_id`,
  ADD KEY `idx_feedback_assignee` (`assignee_id`);

CREATE TABLE IF NOT EXISTS `sys_feedback_dispatch_rule` (
  `id`               bigint       NOT NULL COMMENT '雪花ID',
  `name`             varchar(64)  NOT NULL COMMENT '规则名称',
  `feedback_type`    varchar(32)  NULL COMMENT '反馈类型匹配，空表示任意',
  `keyword`          varchar(128) NULL COMMENT '内容关键词包含匹配，空表示不限制',
  `assignee_id`      bigint       NOT NULL COMMENT '指派处理人',
  `priority`         int          NOT NULL DEFAULT 0 COMMENT '优先级，越大越先匹配',
  `enabled`          tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by`       bigint       NULL,
  `updated_by`       bigint       NULL,
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_dispatch_enabled` (`enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈自动分流规则';

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2214, 'admin:feedback:assign', '指派反馈', 'button', NULL, '手动指派/改派反馈', 1),
(2215, 'admin:feedback-dispatch-rule:list', '查看分流规则', 'page', '/admin/feedback-dispatch-rules', '反馈分流规则', 1),
(2216, 'admin:feedback-dispatch-rule:view', '查看规则详情', 'button', NULL, '规则详情', 1),
(2217, 'admin:feedback-dispatch-rule:create', '新增分流规则', 'button', NULL, '新增规则', 1),
(2218, 'admin:feedback-dispatch-rule:edit', '编辑分流规则', 'button', NULL, '编辑规则', 1),
(2219, 'admin:feedback-dispatch-rule:delete', '删除分流规则', 'button', NULL, '删除规则', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2214, NULL), (1001, 2215, NULL), (1001, 2216, NULL), (1001, 2217, NULL),
(1001, 2218, NULL), (1001, 2219, NULL),
(1003, 2214, NULL), (1003, 2215, NULL), (1003, 2216, NULL), (1003, 2217, NULL),
(1003, 2218, NULL), (1003, 2219, NULL);
