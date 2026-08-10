-- 作者：yangleduo
-- =============================================================================
-- V59: 补全历史灵犀盾事件的算法决策说明
-- =============================================================================

UPDATE `sys_aegis_incident`
SET `decision_summary` = CASE
    WHEN `status` IN ('auto_resolved', 'closed') AND `risk_score` < 35
        THEN CONCAT('风险分 ', `risk_score`, ' < 35，L1 平台自动结案')
    WHEN `status` = 'pending_l3' OR (`requires_l3` = 1 AND `risk_score` >= 85)
        THEN CONCAT('风险分 ', `risk_score`, ' ≥ 85，L2 确认后需 L3 超管终审')
    WHEN `status` IN ('pending_l2', 'confirmed', 'rejected') AND `risk_score` >= 65
        THEN CONCAT('风险分 ', `risk_score`, '，进入 L2 管理账号二次确认（高风险）')
    WHEN `status` = 'pending_l2'
        THEN CONCAT('风险分 ', `risk_score`, '，进入 L2 管理账号二次确认')
    WHEN `risk_score` < 35
        THEN CONCAT('风险分 ', `risk_score`, ' < 35，L1 平台自动结案')
    ELSE CONCAT('风险分 ', `risk_score`, '，由灵犀盾算法分级处置')
END
WHERE `decision_summary` IS NULL OR TRIM(`decision_summary`) = '';
