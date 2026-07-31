-- =============================================================================
-- V18: 管理端公告通知收件箱菜单与权限
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(17, 0, 'notice-inbox', '通知中心', '/admin/notice-inbox', 'views/NoticeInboxView', 'Notifications', 'menu', 'admin:notice:inbox', 6, 0, 1, 0, 1, 1);

-- 公告管理排在通知中心之后，系统配置顺延
UPDATE `sys_admin_menu` SET `sort_order` = 7 WHERE `name` = 'notices';
UPDATE `sys_admin_menu` SET `sort_order` = 8 WHERE `name` = 'settings';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 17);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2143,'admin:notice:inbox','查看管理端通知','page','/admin/notice-inbox','通知中心（已发布管理端公告）',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2143,NULL);
