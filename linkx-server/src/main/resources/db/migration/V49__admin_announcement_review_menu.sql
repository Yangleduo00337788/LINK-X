-- =============================================================================
-- V49: 审核中心「群公告审核」独立菜单（复用 admin:review:list，筛选 targetType=announcement）
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(44, 13, 'announcement-review', '群公告审核', '/admin/announcement-reviews', 'views/ReviewListView', 'Megaphone', 'menu', 'admin:review:list', 2, 0, 1, 0, 1, 1);

UPDATE `sys_admin_menu` SET `sort_order` = 3 WHERE `id` = 15 AND `name` = 'sensitive-word';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 44),
(1004, 44);
