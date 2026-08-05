-- =============================================================================
-- V73: 风控自定义规则链（可 CRUD 条件 + 加分 + 动作）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_risk_rule` (
  `id`              bigint       NOT NULL COMMENT '雪花ID',
  `name`            varchar(64)  NOT NULL COMMENT '规则名称',
  `scope`           varchar(32)  NOT NULL DEFAULT 'global' COMMENT 'global|review|message|simulate',
  `keyword`         varchar(128) NULL COMMENT '文本关键词包含，空表示不限制（兼容简配）',
  `condition_json`  json         NULL COMMENT '扩展条件树',
  `score_delta`     int          NOT NULL DEFAULT 0 COMMENT '命中加分',
  `action_type`     varchar(32)  NOT NULL DEFAULT 'score_only' COMMENT 'score_only|block|alert|escalate',
  `action_config`   json         NULL COMMENT '动作扩展配置',
  `priority`        int          NOT NULL DEFAULT 0 COMMENT '优先级，越大越先展示/记录',
  `enabled`         tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by`      bigint       NULL,
  `updated_by`      bigint       NULL,
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_scope_enabled` (`scope`, `enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控自定义规则';

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2250, 'admin:risk-rule:list', '查看风控规则', 'page', '/admin/risk-rules', '风控自定义规则', 1),
(2251, 'admin:risk-rule:view', '查看风控规则详情', 'button', NULL, '规则详情', 1),
(2252, 'admin:risk-rule:create', '新增风控规则', 'button', NULL, '新增规则', 1),
(2253, 'admin:risk-rule:edit', '编辑风控规则', 'button', NULL, '编辑规则', 1),
(2254, 'admin:risk-rule:delete', '删除风控规则', 'button', NULL, '删除规则', 1),
(2255, 'admin:risk-rule:simulate', '风控规则模拟', 'button', NULL, '单条规则模拟', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2250, NULL), (1001, 2251, NULL), (1001, 2252, NULL), (1001, 2253, NULL), (1001, 2254, NULL), (1001, 2255, NULL),
(1005, 2250, NULL), (1005, 2251, NULL), (1005, 2252, NULL), (1005, 2253, NULL), (1005, 2254, NULL), (1005, 2255, NULL);

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(72, 58, 'risk-rules', '风控规则', '/admin/risk-rules', 'views/RiskRuleListView', 'Shield', 'menu', 'admin:risk-rule:list', 4, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 72), (1005, 72);
