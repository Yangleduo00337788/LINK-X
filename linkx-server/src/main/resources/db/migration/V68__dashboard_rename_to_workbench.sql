-- 作者：yangleduo
-- V68: 仪表盘菜单/权限更名为工作台
UPDATE `sys_admin_menu`
SET `title` = '工作台'
WHERE `name` = 'dashboard' OR `id` = 1;

UPDATE `sys_permission`
SET `permission_name` = '查看工作台',
    `description` = '工作台'
WHERE `permission_code` = 'admin:dashboard:view';
