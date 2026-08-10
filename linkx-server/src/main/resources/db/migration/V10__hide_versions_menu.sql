-- 作者：yangleduo
-- 版本配置已并入「系统配置 > 客户端配置」，隐藏并停用独立「版本管理」菜单
UPDATE `sys_admin_menu`
SET `hidden` = 1,
    `status` = 0,
    `deleted` = 1,
    `updated_at` = NOW()
WHERE `name` = 'versions'
   OR `path` = '/admin/versions';
