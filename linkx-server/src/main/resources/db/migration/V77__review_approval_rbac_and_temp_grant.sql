-- 作者：yangleduo
-- =============================================================================
-- V77: 收紧审核/审批 RBAC + 审批临时授权表 + 在线管理员权限
-- =============================================================================

-- 安全管理员不应拥有审批操作权限（仅 audit_admin / admin）
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1005 AND `permission_id` IN (2270, 2274, 2275, 2276);

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1005 AND `menu_id` IN (75, 76);

-- 只读观察员不应查看/处理审核内容
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1006 AND `permission_id` IN (2129, 2155);

DELETE FROM `sys_admin_role_menu`
WHERE `role_id` = 1006 AND `menu_id` IN (13, 14, 43, 44);

-- 审批临时授权（无审核角色用户被指定为审批人时临时放开权限）
CREATE TABLE IF NOT EXISTS `sys_approval_temp_grant` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `record_id`       bigint       NOT NULL COMMENT '审批记录ID',
  `user_id`         bigint       NOT NULL COMMENT '被授权用户',
  `permission_code` varchar(64)  NOT NULL COMMENT '临时权限码',
  `granted_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at`      datetime     NULL COMMENT '撤销时间',
  PRIMARY KEY (`id`),
  KEY `idx_temp_grant_user_active` (`user_id`, `revoked_at`),
  KEY `idx_temp_grant_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批临时权限授权';

-- 在线管理员查看
INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2277, 'admin:online:list', '查看在线管理员', 'page', NULL, '查看当前在线管理员人数与列表', 1);

INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`) VALUES
(297277, 1001, 2277, NULL, 0),
(297287, 1004, 2277, NULL, 0),
(297297, 1005, 2277, NULL, 0),
(297307, 1006, 2277, NULL, 0);
