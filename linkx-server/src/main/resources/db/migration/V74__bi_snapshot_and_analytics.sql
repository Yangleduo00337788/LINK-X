-- =============================================================================
-- V74: BI 快照表 + 高级分析 / 实时大屏权限菜单
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_admin_statistic_snapshot` (
  `id`              bigint       NOT NULL COMMENT '雪花ID',
  `snapshot_date`   date         NOT NULL COMMENT '统计日期',
  `metric_domain`   varchar(32)  NOT NULL DEFAULT 'statistic' COMMENT 'statistic|dashboard',
  `metric_key`      varchar(64)  NOT NULL COMMENT '指标键',
  `dimension_key`   varchar(64)  NOT NULL DEFAULT 'all' COMMENT '维度键',
  `dimension_value` varchar(128) NULL COMMENT '维度值',
  `metric_value`    bigint       NOT NULL DEFAULT 0 COMMENT '指标值',
  `extra_json`      json         NULL COMMENT '扩展数据',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_snapshot` (`snapshot_date`, `metric_domain`, `metric_key`, `dimension_key`, `dimension_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端统计快照';

CREATE TABLE IF NOT EXISTS `sys_admin_dashboard_snapshot` (
  `id`              bigint       NOT NULL COMMENT '雪花ID',
  `snapshot_date`   date         NOT NULL COMMENT '统计日期',
  `summary_json`    json         NOT NULL COMMENT '工作台 KPI 摘要 JSON',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dashboard_snapshot_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台每日快照';

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2260, 'admin:bi:view', '高级 BI 分析', 'page', '/admin/bi-analytics', '自定义维度/对比/下钻', 1),
(2261, 'admin:big-screen:view', '实时大屏', 'page', '/admin/big-screen', 'WebSocket/SSE 大屏', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2260, NULL), (1001, 2261, NULL),
(1003, 2260, NULL), (1005, 2260, NULL), (1005, 2261, NULL);

INSERT IGNORE INTO `sys_admin_menu`
(`id`, `parent_id`, `name`, `title`, `path`, `component`, `icon`, `menu_type`, `permission_code`, `sort_order`, `hidden`, `cacheable`, `external_link`, `keep_alive`, `status`, `deleted`) VALUES
(73, 18, 'bi-analytics', '高级分析', '/admin/bi-analytics', 'views/BiAnalyticsView', 'Analytics', 'menu', 'admin:bi:view', 2, 0, 1, 0, 1, 1, 0),
(74, 18, 'big-screen', '实时大屏', '/admin/big-screen', 'views/BigScreenView', 'Tv', 'menu', 'admin:big-screen:view', 3, 0, 0, 0, 0, 1, 0);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 73), (1001, 74), (1003, 73), (1005, 73), (1005, 74);
