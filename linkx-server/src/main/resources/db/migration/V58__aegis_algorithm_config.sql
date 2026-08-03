-- =============================================================================
-- V58: 灵犀盾算法阈值可配置
-- =============================================================================

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `aegis_score_l2_min` int NOT NULL DEFAULT 35 COMMENT '≥此分进入 L2 待确认' AFTER `aegis_l3_sla_hours`,
  ADD COLUMN `aegis_score_l3_min` int NOT NULL DEFAULT 85 COMMENT '≥此分标记需 L3 终审' AFTER `aegis_score_l2_min`,
  ADD COLUMN `aegis_level_medium_min` int NOT NULL DEFAULT 40 COMMENT '中风险分数线' AFTER `aegis_score_l3_min`,
  ADD COLUMN `aegis_level_high_min` int NOT NULL DEFAULT 65 COMMENT '高风险分数线' AFTER `aegis_level_medium_min`,
  ADD COLUMN `aegis_level_critical_min` int NOT NULL DEFAULT 85 COMMENT '危急风险分数线' AFTER `aegis_level_high_min`,
  ADD COLUMN `aegis_repeat_bonus_light` int NOT NULL DEFAULT 5 COMMENT '24h内重复违规加分(≥1次)' AFTER `aegis_level_critical_min`,
  ADD COLUMN `aegis_repeat_bonus_heavy` int NOT NULL DEFAULT 15 COMMENT '24h内重复违规加分(≥3次)' AFTER `aegis_repeat_bonus_light`;

ALTER TABLE `sys_aegis_incident`
  ADD COLUMN `base_score` int DEFAULT NULL COMMENT '算法基础分' AFTER `risk_score`,
  ADD COLUMN `repeat_bonus` int NOT NULL DEFAULT 0 COMMENT '重复违规加分' AFTER `base_score`,
  ADD COLUMN `decision_summary` varchar(512) DEFAULT NULL COMMENT '算法决策说明' AFTER `requires_l3`;
