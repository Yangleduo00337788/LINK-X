-- 作者：yangleduo
-- =============================================================================
-- V46: 反馈 SLA 小时数（默认 24；超期未处理标记 overdue）
-- =============================================================================

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `feedback_sla_hours` INT NOT NULL DEFAULT 24
    COMMENT '反馈处理 SLA（小时）' AFTER `support_phone`;
