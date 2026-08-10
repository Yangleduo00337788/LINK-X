-- 作者：yangleduo
-- =============================================================================
-- V22: 导出 / 批量审核 / 权限 CRUD / 角色绑用户 权限码
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2152,'admin:review:batch','批量审核','button',NULL,'批量通过/驳回',1),
(2153,'admin:user:export','导出用户数据','button',NULL,'用户列表导出',1),
(2154,'admin:feedback:export','导出反馈数据','button',NULL,'反馈列表导出',1),
(2155,'admin:review:export','导出审核数据','button',NULL,'审核列表导出',1),
(2156,'admin:risk-event:export','导出风险事件','button',NULL,'风险事件导出',1),
(2157,'admin:permission:create','新增权限','button',NULL,'新增权限点',1),
(2158,'admin:permission:edit','编辑权限','button',NULL,'编辑权限点',1),
(2159,'admin:permission:delete','删除权限','button',NULL,'删除权限点',1),
(2160,'admin:role:assign-user','角色分配用户','button',NULL,'角色绑定用户',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2152,NULL),
(1001,2153,NULL),
(1001,2154,NULL),
(1001,2155,NULL),
(1001,2156,NULL),
(1001,2157,NULL),
(1001,2158,NULL),
(1001,2159,NULL),
(1001,2160,NULL);
