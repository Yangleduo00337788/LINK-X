-- 作者：yangleduo
-- =============================================================================
-- V20: 风险事件表 + 日志中心菜单/权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_risk_event` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型: SENSITIVE_WORD_MATCH/MESSAGE_STORM',
  `title` VARCHAR(128) NOT NULL COMMENT '标题摘要',
  `detail` TEXT DEFAULT NULL COMMENT '详情',
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'medium' COMMENT '风险等级: low/medium/high',
  `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/handled/ignored',
  `user_id` BIGINT DEFAULT NULL COMMENT '关联用户ID',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '关联用户名',
  `target_resource_id` VARCHAR(128) DEFAULT NULL COMMENT '目标资源ID',
  `target_resource_type` VARCHAR(64) DEFAULT NULL COMMENT '目标资源类型',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT 'IP',
  `extra_data` VARCHAR(2048) DEFAULT NULL COMMENT '扩展信息',
  `audit_log_id` BIGINT DEFAULT NULL COMMENT '关联审计日志ID',
  `resolution` VARCHAR(1000) DEFAULT NULL COMMENT '处置意见',
  `handled_by` BIGINT DEFAULT NULL COMMENT '处置人',
  `handled_at` DATETIME DEFAULT NULL COMMENT '处置时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_audit_log_id` (`audit_log_id`),
  KEY `idx_risk_status` (`status`),
  KEY `idx_risk_event_type` (`event_type`),
  KEY `idx_risk_create_time` (`create_time`),
  KEY `idx_risk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端风险事件';

-- 回填历史敏感词命中 / 消息风暴审计为待处置风险事件
INSERT IGNORE INTO `sys_risk_event`
(`id`, `event_type`, `title`, `detail`, `risk_level`, `status`, `user_id`, `username`,
 `target_resource_id`, `target_resource_type`, `ip`, `extra_data`, `audit_log_id`, `create_time`, `update_time`)
SELECT
  a.`id`,
  a.`operation_type`,
  CASE
    WHEN a.`operation_type` = 'MESSAGE_STORM' THEN '消息风暴'
    ELSE '敏感词命中'
  END,
  a.`description`,
  CASE
    WHEN a.`operation_type` = 'MESSAGE_STORM' THEN 'high'
    WHEN a.`failure_reason` = 'blocked' THEN 'high'
    WHEN a.`failure_reason` = 'alert' THEN 'medium'
    ELSE 'low'
  END,
  'pending',
  a.`user_id`,
  a.`username`,
  a.`target_resource_id`,
  a.`target_resource_type`,
  a.`ip`,
  a.`extra_data`,
  a.`id`,
  a.`create_time`,
  a.`create_time`
FROM `sys_audit_log` a
WHERE a.`operation_type` IN ('SENSITIVE_WORD_MATCH', 'MESSAGE_STORM');

-- 菜单：日志中心下新增「风险事件」
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(19, 7, 'risk-event', '风险事件', '/admin/risk-events', 'views/RiskEventView', 'Warning', 'menu', 'admin:risk-event:list', 3, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 19);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2146,'admin:risk-event:list','查看风险事件','page','/admin/risk-events','风险事件列表',1),
(2147,'admin:risk-event:view','查看风险事件详情','button',NULL,'风险事件详情',1),
(2148,'admin:risk-event:handle','处置风险事件','button',NULL,'风险事件处置',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2146,NULL),
(1001,2147,NULL),
(1001,2148,NULL);
