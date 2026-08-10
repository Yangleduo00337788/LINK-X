-- 作者：yangleduo
-- =============================================================================
-- V57: 灵犀盾 (LinkX Aegis) — 统一安全算法平台
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_aegis_incident` (
  `id`                   bigint       NOT NULL COMMENT '雪花 ID',
  `incident_no`          varchar(32)  NOT NULL COMMENT '事件编号 AEG-YYYYMMDD-序号',
  `source_type`          varchar(32)  NOT NULL COMMENT 'sensitive_word|user_report|message_storm|rate_limit|login_lock|review',
  `source_ref_id`        bigint       DEFAULT NULL COMMENT '关联 risk_event / review_task / feedback ID',
  `title`                varchar(256) NOT NULL COMMENT '标题',
  `detail`               text         DEFAULT NULL COMMENT '详情',
  `risk_score`           int          NOT NULL DEFAULT 0 COMMENT '算法风险分 0-100',
  `risk_level`           varchar(16)  NOT NULL DEFAULT 'low' COMMENT 'low|medium|high|critical',
  `current_tier`         varchar(8)   NOT NULL DEFAULT 'L1' COMMENT 'L1 平台|L2 管理|L3 超管',
  `status`               varchar(32)  NOT NULL DEFAULT 'pending_l2' COMMENT 'auto_resolved|pending_l2|pending_l3|confirmed|rejected|closed',
  `auto_action`          varchar(32)  DEFAULT NULL COMMENT 'L1 自动处置动作',
  `enforcement_status`   varchar(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|applied|skipped|failed',
  `user_id`              bigint       DEFAULT NULL,
  `username`             varchar(64)  DEFAULT NULL,
  `target_resource_type` varchar(64)  DEFAULT NULL,
  `target_resource_id`   varchar(128) DEFAULT NULL,
  `ip`                   varchar(64)  DEFAULT NULL,
  `extra_data`           text         DEFAULT NULL COMMENT 'JSON 扩展',
  `assignee_role`        varchar(32)  DEFAULT NULL COMMENT 'L2 目标角色 audit_admin|security_admin',
  `assigned_admin_id`    bigint       DEFAULT NULL,
  `l2_operator_id`       bigint       DEFAULT NULL,
  `l2_action`            varchar(32)  DEFAULT NULL COMMENT 'confirm|reject|escalate',
  `l2_resolution`        varchar(512) DEFAULT NULL,
  `l2_at`                datetime     DEFAULT NULL,
  `l3_operator_id`       bigint       DEFAULT NULL,
  `l3_action`            varchar(32)  DEFAULT NULL COMMENT 'approve|reject|override',
  `l3_resolution`        varchar(512) DEFAULT NULL,
  `l3_at`                datetime     DEFAULT NULL,
  `requires_l3`          tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否需三级终审',
  `escalated_at`         datetime     DEFAULT NULL,
  `escalation_count`     int          NOT NULL DEFAULT 0,
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aegis_incident_no` (`incident_no`),
  KEY `idx_aegis_status_tier` (`status`, `current_tier`),
  KEY `idx_aegis_source` (`source_type`, `source_ref_id`),
  KEY `idx_aegis_user` (`user_id`),
  KEY `idx_aegis_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灵犀盾安全事件';

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `aegis_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '灵犀盾平台总开关' AFTER `feedback_escalation_interval_hours`,
  ADD COLUMN `aegis_auto_enforce` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'L1 平台自动执法' AFTER `aegis_enabled`,
  ADD COLUMN `aegis_l2_sla_hours` int NOT NULL DEFAULT 4 COMMENT 'L2 待确认 SLA（小时）' AFTER `aegis_auto_enforce`,
  ADD COLUMN `aegis_l3_sla_hours` int NOT NULL DEFAULT 2 COMMENT 'L3 待终审 SLA（小时）' AFTER `aegis_l2_sla_hours`;

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(48, 0, 'aegis', '灵犀盾', '/admin/aegis', 'views/AegisPlatformView', 'Shield', 'menu', 'admin:aegis:list', 3, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2230, 'admin:aegis:list', '查看灵犀盾事件', 'page', '/admin/aegis', '灵犀盾安全平台', 1),
(2231, 'admin:aegis:confirm', '灵犀盾二级确认', 'button', NULL, 'L2 管理账号二次确认', 1),
(2232, 'admin:aegis:approve', '灵犀盾三级终审', 'button', NULL, 'L3 超管终审', 1),
(2233, 'admin:aegis:settings', '灵犀盾平台配置', 'button', NULL, '启停平台与算法参数', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2230, NULL), (1001, 2231, NULL), (1001, 2232, NULL), (1001, 2233, NULL),
(1004, 2230, NULL), (1004, 2231, NULL),
(1005, 2230, NULL), (1005, 2231, NULL);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 48), (1004, 48), (1005, 48);
