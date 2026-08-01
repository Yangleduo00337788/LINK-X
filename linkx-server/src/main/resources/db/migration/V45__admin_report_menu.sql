-- =============================================================================
-- V45: 审核中心「用户举报」独立菜单（复用 admin:review:list，筛选 sourceType=report）
-- =============================================================================

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(43, 13, 'report-task', '用户举报', '/admin/reports', 'views/ReviewListView', 'Flag', 'menu', 'admin:review:list', 0, 0, 1, 0, 1, 1);

-- 违规内容 / 敏感词顺延
UPDATE `sys_admin_menu` SET `sort_order` = 1 WHERE `id` = 14 AND `name` = 'review-task';
UPDATE `sys_admin_menu` SET `sort_order` = 2 WHERE `id` = 15 AND `name` = 'sensitive-word';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 43),
(1004, 43);
