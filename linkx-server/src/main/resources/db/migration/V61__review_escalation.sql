-- 作者：yangleduo
-- =============================================================================
-- V61: 审核任务 SLA 超时督办
-- =============================================================================

ALTER TABLE `sys_review_task`
  ADD COLUMN `escalated_at` datetime NULL COMMENT '最近一次督办时间' AFTER `assignee_id`,
  ADD COLUMN `escalation_count` int NOT NULL DEFAULT 0 COMMENT '督办次数' AFTER `escalated_at`,
  ADD KEY `idx_review_escalated` (`status`, `escalated_at`);

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `review_sla_hours` int NOT NULL DEFAULT 24 COMMENT '审核任务 SLA（小时）' AFTER `feedback_escalation_interval_hours`,
  ADD COLUMN `review_escalation_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用审核超时督办' AFTER `review_sla_hours`,
  ADD COLUMN `review_escalation_interval_hours` int NOT NULL DEFAULT 24 COMMENT '同一审核任务重复督办间隔（小时）' AFTER `review_escalation_enabled`;
