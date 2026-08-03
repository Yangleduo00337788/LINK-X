-- =============================================================================
-- V55: 反馈 SLA 超时升级/改派
-- =============================================================================

ALTER TABLE `sys_feedback`
  ADD COLUMN `escalated_at` datetime NULL COMMENT '最近一次升级时间' AFTER `assigned_at`,
  ADD COLUMN `escalation_count` int NOT NULL DEFAULT 0 COMMENT '升级次数' AFTER `escalated_at`,
  ADD KEY `idx_feedback_escalated` (`status`, `escalated_at`);

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `feedback_escalation_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用反馈超时升级' AFTER `feedback_sla_hours`,
  ADD COLUMN `feedback_escalation_auto_reassign` tinyint(1) NOT NULL DEFAULT 1 COMMENT '升级时尝试自动改派' AFTER `feedback_escalation_enabled`,
  ADD COLUMN `feedback_escalation_interval_hours` int NOT NULL DEFAULT 24 COMMENT '同一工单重复升级间隔（小时）' AFTER `feedback_escalation_auto_reassign`;
