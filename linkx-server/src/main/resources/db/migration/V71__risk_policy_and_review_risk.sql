-- =============================================================================
-- V71: 风控策略可视化配置 + 审核风险上下文支撑
-- =============================================================================

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `risk_storm_user_threshold` int NOT NULL DEFAULT 30 COMMENT '用户消息风暴阈值（窗口内条数）' AFTER `review_escalation_interval_hours`,
  ADD COLUMN `risk_storm_user_window_seconds` int NOT NULL DEFAULT 10 COMMENT '用户消息风暴窗口（秒）' AFTER `risk_storm_user_threshold`,
  ADD COLUMN `risk_storm_group_min_members` int NOT NULL DEFAULT 500 COMMENT '群风暴最低成员数' AFTER `risk_storm_user_window_seconds`,
  ADD COLUMN `risk_storm_group_large_members` int NOT NULL DEFAULT 1000 COMMENT '大群成员数分界' AFTER `risk_storm_group_min_members`,
  ADD COLUMN `risk_storm_group_mid_per_minute` int NOT NULL DEFAULT 10 COMMENT '中群每分钟上限' AFTER `risk_storm_group_large_members`,
  ADD COLUMN `risk_storm_group_large_per_minute` int NOT NULL DEFAULT 5 COMMENT '大群每分钟上限' AFTER `risk_storm_group_mid_per_minute`,
  ADD COLUMN `risk_score_medium_min` int NOT NULL DEFAULT 40 COMMENT '中风险分数线' AFTER `risk_storm_group_large_per_minute`,
  ADD COLUMN `risk_score_high_min` int NOT NULL DEFAULT 65 COMMENT '高风险分数线' AFTER `risk_score_medium_min`,
  ADD COLUMN `risk_score_critical_min` int NOT NULL DEFAULT 85 COMMENT '危急风险分数线' AFTER `risk_score_high_min`,
  ADD COLUMN `rate_limit_login_per_minute` int NOT NULL DEFAULT 10 COMMENT '登录接口每分钟限流' AFTER `risk_score_critical_min`,
  ADD COLUMN `rate_limit_register_per_minute` int NOT NULL DEFAULT 5 COMMENT '注册接口每分钟限流' AFTER `rate_limit_login_per_minute`,
  ADD COLUMN `rate_limit_search_per_minute` int NOT NULL DEFAULT 30 COMMENT '搜索接口每分钟限流' AFTER `rate_limit_register_per_minute`,
  ADD COLUMN `rate_limit_list_per_minute` int NOT NULL DEFAULT 60 COMMENT '列表接口每分钟限流' AFTER `rate_limit_search_per_minute`,
  ADD COLUMN `rate_limit_write_per_minute` int NOT NULL DEFAULT 30 COMMENT '写接口每分钟限流' AFTER `rate_limit_list_per_minute`,
  ADD COLUMN `rate_limit_upload_per_minute` int NOT NULL DEFAULT 20 COMMENT '上传接口每分钟限流' AFTER `rate_limit_write_per_minute`;

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(71, 58, 'risk-policy', '风控策略', '/admin/risk-policies', 'views/RiskPolicyView', 'Options', 'menu', 'admin:risk-policy:list', 3, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2240, 'admin:risk-policy:list', '查看风控策略', 'page', '/admin/risk-policies', '风控策略可视化', 1),
(2241, 'admin:risk-policy:edit', '编辑风控策略', 'button', NULL, '更新阈值与命中模拟', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2240, NULL), (1001, 2241, NULL),
(1005, 2240, NULL), (1005, 2241, NULL);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 71), (1005, 71);
