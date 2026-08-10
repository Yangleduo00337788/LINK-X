-- 作者：yangleduo
-- =============================================================================
-- V81: RBAC 修复
-- 1. ops_admin 恢复公告列表权限（V80 误删 admin:notice:list）
-- 2. audit_admin 补齐异常访问权限（修复 V72/V52 的 2220 ID 冲突导致绑定失效）
-- 3. audit_admin 补齐设备封禁/解封
-- 4. readonly_observer 移除统计导出
-- =============================================================================

-- 确保异常访问权限码落在 2220/2221（V72 曾误占 ID，值班表已迁至 2280+）
INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2220, 'admin:abnormal-access:list', '查看异常访问', 'page', '/admin/abnormal-access', '统一异常访问记录', 1),
(2221, 'admin:abnormal-access:export', '导出异常访问', 'button', NULL, '异常访问记录导出', 1);

UPDATE `sys_permission`
SET `permission_code` = 'admin:abnormal-access:list',
    `permission_name` = '查看异常访问',
    `resource_type` = 'page',
    `resource_path` = '/admin/abnormal-access',
    `description` = '统一异常访问记录',
    `status` = 1
WHERE `id` = 2220
  AND `permission_code` <> 'admin:abnormal-access:list';

UPDATE `sys_permission`
SET `permission_code` = 'admin:abnormal-access:export',
    `permission_name` = '导出异常访问',
    `resource_type` = 'button',
    `description` = '异常访问记录导出',
    `status` = 1
WHERE `id` = 2221
  AND `permission_code` <> 'admin:abnormal-access:export';

-- ---------- 1. ops_admin(1003)：恢复公告列表页权限 ----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT 298301, 1003, 2136, NULL, 0
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = 2136 AND IFNULL(p.`deleted`, 0) = 0)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1003 AND rp.`permission_id` = 2136 AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = 298301);

-- ---------- 2. audit_admin(1004)：异常访问（按 permission_code 幂等绑定）----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.bind_id, 1004, p.`id`, NULL, 0
FROM (
  SELECT 298401 AS bind_id, 'admin:abnormal-access:list' AS code UNION ALL
  SELECT 298402, 'admin:abnormal-access:export'
) t
INNER JOIN `sys_permission` p ON p.`permission_code` = t.code AND IFNULL(p.`deleted`, 0) = 0
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1004 AND rp.`permission_id` = p.`id` AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.bind_id);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES (1004, 45);

-- ---------- 3. audit_admin(1004)：设备封禁/解封 ----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1004, t.permission_id, NULL, 0
FROM (
  SELECT 298411 AS id, 2182 AS permission_id UNION ALL
  SELECT 298412, 2183
) t
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.permission_id AND IFNULL(p.`deleted`, 0) = 0)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1004 AND rp.`permission_id` = t.permission_id AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.id);

-- ---------- 4. readonly_observer(1006)：移除统计导出 ----------
DELETE FROM `sys_role_permission`
WHERE `role_id` = 1006 AND `permission_id` = 2145;
