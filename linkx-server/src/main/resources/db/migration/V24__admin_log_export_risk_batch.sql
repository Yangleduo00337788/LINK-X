-- 作者：yangleduo
-- =============================================================================
-- V24: 操作/登录日志导出 + 风险事件批量处置 权限码
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2168,'admin:audit:export','导出操作日志','button',NULL,'操作日志导出',1),
(2169,'admin:login-log:export','导出登录日志','button',NULL,'登录日志导出',1),
(2170,'admin:risk-event:batch','批量处置风险事件','button',NULL,'批量处理/忽略风险事件',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2168,NULL),
(1001,2169,NULL),
(1001,2170,NULL);
