-- device / blacklist export permissions
INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2174,'admin:device:export','export devices','button',NULL,'device session export',1),
(2175,'admin:blacklist:export','export blacklist','button',NULL,'blacklist export',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2174,NULL),
(1001,2175,NULL);

INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`create_by`,`deleted`) VALUES
(294174,1004,2174,NULL,0),
(294175,1004,2175,NULL,0),
(295174,1005,2174,NULL,0),
(295175,1005,2175,NULL,0),
-- readonly can export devices; no blacklist menu so no blacklist export
(296174,1006,2174,NULL,0);
