-- =============================================================================
-- V78: 修复权限 ID 冲突后的角色绑定缺失 + 补全值班表权限
-- 原因：V72 试图占用 2220–2225（已被 V52/V53/V56 占用），duty-schedule 权限从未写入；
--       大量迁移使用无 id 的 INSERT IGNORE 写入 sys_role_permission，生产库静默跳过。
-- =============================================================================

-- ---------- 1. 值班表权限（新 ID，避免与 2220–2225 冲突）----------
INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2280, 'admin:duty-schedule:list',   '查看值班表',     'page',   '/admin/duty-schedules', '反馈值班表', 1),
(2281, 'admin:duty-schedule:view',   '查看值班表详情', 'button', NULL, '值班表详情', 1),
(2282, 'admin:duty-schedule:create', '新增值班表',     'button', NULL, '新增值班表', 1),
(2283, 'admin:duty-schedule:edit',   '编辑值班表',     'button', NULL, '编辑值班表', 1),
(2284, 'admin:duty-schedule:delete', '删除值班表',     'button', NULL, '删除值班表', 1);

-- 确保风控策略/规则、定时任务、分流模拟等权限存在（幂等）
INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2224, 'admin:scheduled-task:list',    '查看定时任务', 'page',   '/admin/scheduled-tasks', '定时任务列表', 1),
(2225, 'admin:feedback-dispatch-rule:simulate', '规则模拟', 'button', NULL, '反馈分流规则模拟', 1),
(2227, 'admin:scheduled-task:console', '打开 SnailJob 控制台', 'button', NULL, '跳转调度中心进行 cron/启停等写操作', 1),
(2240, 'admin:risk-policy:list', '查看风控策略', 'page', '/admin/risk-policies', '风控策略可视化', 1),
(2241, 'admin:risk-policy:edit', '编辑风控策略', 'button', NULL, '更新阈值与命中模拟', 1),
(2250, 'admin:risk-rule:list',     '查看风控规则', 'page', '/admin/risk-rules', '风控自定义规则', 1),
(2251, 'admin:risk-rule:view',     '查看风控规则详情', 'button', NULL, '规则详情', 1),
(2252, 'admin:risk-rule:create',   '新增风控规则', 'button', NULL, '新增规则', 1),
(2253, 'admin:risk-rule:edit',     '编辑风控规则', 'button', NULL, '编辑规则', 1),
(2254, 'admin:risk-rule:delete',   '删除风控规则', 'button', NULL, '删除规则', 1),
(2255, 'admin:risk-rule:simulate', '风控规则模拟', 'button', NULL, '单条规则模拟', 1);

-- 值班表菜单指向新权限码
UPDATE `sys_admin_menu`
SET `permission_code` = 'admin:duty-schedule:list'
WHERE `id` = 103 AND `name` = 'duty-schedules';

-- ---------- 2. ops_admin(1003)：分流/首页/值班/BI/定时任务监控 ----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1003, t.permission_id, NULL, 0
FROM (
  SELECT 298001 AS id, 2214 AS permission_id UNION ALL
  SELECT 298002, 2215 UNION ALL
  SELECT 298003, 2216 UNION ALL
  SELECT 298004, 2217 UNION ALL
  SELECT 298005, 2218 UNION ALL
  SELECT 298006, 2219 UNION ALL
  SELECT 298007, 2222 UNION ALL
  SELECT 298008, 2223 UNION ALL
  SELECT 298009, 2224 UNION ALL
  SELECT 298010, 2225 UNION ALL
  SELECT 298011, 2260 UNION ALL
  SELECT 298012, 2280 UNION ALL
  SELECT 298013, 2281 UNION ALL
  SELECT 298014, 2282 UNION ALL
  SELECT 298015, 2283 UNION ALL
  SELECT 298016, 2284
) t
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.permission_id)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1003 AND rp.`permission_id` = t.permission_id AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.id);

-- ---------- 3. security_admin(1005)：异常访问/风控策略/规则/BI/大屏 ----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1005, t.permission_id, NULL, 0
FROM (
  SELECT 298101 AS id, 2220 AS permission_id UNION ALL
  SELECT 298102, 2221 UNION ALL
  SELECT 298103, 2240 UNION ALL
  SELECT 298104, 2241 UNION ALL
  SELECT 298105, 2250 UNION ALL
  SELECT 298106, 2251 UNION ALL
  SELECT 298107, 2252 UNION ALL
  SELECT 298108, 2253 UNION ALL
  SELECT 298109, 2254 UNION ALL
  SELECT 298110, 2255 UNION ALL
  SELECT 298111, 2260 UNION ALL
  SELECT 298112, 2261
) t
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.permission_id)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1005 AND rp.`permission_id` = t.permission_id AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.id);

-- ---------- 4. 菜单绑定（幂等）----------
INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1003, 102),
(1003, 103),
(1005, 71),
(1005, 72);
