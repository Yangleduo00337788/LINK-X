-- 作者：yangleduo
-- AI 内容机审：开关与人审分数上限

ALTER TABLE `sys_runtime_setting`
    ADD COLUMN `ai_review_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用AI内容机审' AFTER `risk_score_critical_min`,
    ADD COLUMN `ai_review_human_score_min` INT DEFAULT 65 COMMENT 'AI风险分达到该值送人审' AFTER `ai_review_enabled`;
