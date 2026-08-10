-- 作者：yangleduo
-- =============================================================================
-- V66: 系统监控子菜单 + 指标快照表（趋势图）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_monitor_metric_snapshot` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `snapshot_at`  DATETIME     NOT NULL COMMENT '采样时间',
  `category`     VARCHAR(32)  NOT NULL COMMENT 'redis/jvm/http/hikari',
  `metric_key`   VARCHAR(64)  NOT NULL COMMENT '指标键',
  `metric_value` DOUBLE       NOT NULL COMMENT '数值',
  `extra_json`   VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_monitor_snapshot_cat_time` (`category`, `snapshot_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统监控指标快照';

-- 父菜单改为目录
UPDATE `sys_admin_menu`
SET `parent_id` = 0,
    `name` = 'system-monitor',
    `title` = '系统监控',
    `path` = '/admin/system-monitor',
    `component` = NULL,
    `icon` = 'Pulse',
    `menu_type` = 'dir',
    `permission_code` = 'admin:system-monitor:view',
    `sort_order` = 9
WHERE `id` = 49;

-- 子菜单
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(50, 49, 'monitor-cache',  '缓存监控', '/admin/system-monitor/cache',      'views/monitor/CacheMonitorView',      'Layers',     'menu', 'admin:system-monitor:view', 1, 0, 1, 0, 1, 1),
(51, 49, 'monitor-service','服务监控', '/admin/system-monitor/service',    'views/monitor/ServiceMonitorView',    'Server',     'menu', 'admin:system-monitor:view', 2, 0, 1, 0, 1, 1),
(52, 49, 'monitor-api',    'API访问统计', '/admin/system-monitor/api-stats','views/monitor/ApiStatsMonitorView',   'Analytics',  'menu', 'admin:system-monitor:view', 3, 0, 1, 0, 1, 1),
(53, 49, 'monitor-tasks',  '定时任务', '/admin/system-monitor/tasks',      'views/monitor/TaskMonitorView',       'Timer',      'menu', 'admin:system-monitor:view', 4, 0, 1, 0, 1, 1),
(54, 49, 'monitor-sql',    'SQL监控', '/admin/system-monitor/sql',         'views/monitor/SqlMonitorView',        'Code',       'menu', 'admin:system-monitor:view', 5, 0, 1, 0, 1, 1);

-- 原独立定时任务菜单隐藏（功能迁入系统监控）
UPDATE `sys_admin_menu` SET `hidden` = 1, `sort_order` = 99 WHERE `id` = 47 AND `name` = 'scheduled-tasks';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 50), (1001, 51), (1001, 52), (1001, 53), (1001, 54),
(1003, 50), (1003, 51), (1003, 52), (1003, 53), (1003, 54);
