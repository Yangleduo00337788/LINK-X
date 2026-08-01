-- 设备 / 黑名单列表导出权限
INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2174,'admin:device:export','导出设备列表','button',NULL,'设备会话导出',1),
(2175,'admin:blacklist:export','导出黑名单','button',NULL,'黑名单导出',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2174,NULL),
(1001,2175,NULL);

INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`create_by`,`deleted`) VALUES
(294174,1004,2174,NULL,0),
(294175,1004,2175,NULL,0),
(295174,1005,2174,NULL,0),
(295175,1005,2175,NULL,0),
-- 只读可导出设备（有设备列表）；黑名单无菜单故不授导出
(296174,1006,2174,NULL,0);
