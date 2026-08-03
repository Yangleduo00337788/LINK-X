-- =============================================================================
-- V60: 移除灵犀盾 (LinkX Aegis) 平台
-- =============================================================================

DROP TABLE IF EXISTS `sys_aegis_incident`;

DELETE FROM `sys_scheduled_task` WHERE `task_key` = 'aegis_escalation';

DELETE FROM `sys_admin_role_menu` WHERE `menu_id` = 48;
DELETE FROM `sys_role_permission` WHERE `permission_id` IN (2230, 2231, 2232, 2233);
DELETE FROM `sys_permission` WHERE `id` IN (2230, 2231, 2232, 2233);
DELETE FROM `sys_admin_menu` WHERE `id` = 48;

ALTER TABLE `sys_runtime_setting`
  DROP COLUMN `aegis_enabled`,
  DROP COLUMN `aegis_auto_enforce`,
  DROP COLUMN `aegis_l2_sla_hours`,
  DROP COLUMN `aegis_l3_sla_hours`,
  DROP COLUMN `aegis_score_l2_min`,
  DROP COLUMN `aegis_score_l3_min`,
  DROP COLUMN `aegis_level_medium_min`,
  DROP COLUMN `aegis_level_high_min`,
  DROP COLUMN `aegis_level_critical_min`,
  DROP COLUMN `aegis_repeat_bonus_light`,
  DROP COLUMN `aegis_repeat_bonus_heavy`;
