-- 作者：yangleduo
-- =============================================================================
-- V47: 补齐角色权限绑定
-- 原因：V32/V39/V40/V44 等使用
--   INSERT IGNORE INTO sys_role_permission (role_id, permission_id, create_by)
-- 未提供主键 id；表 id 无自增时，INSERT IGNORE 会静默跳过，Flyway 仍标记成功。
-- 菜单绑定往往已生效，导致「看得见菜单、接口 403」。
-- 本脚本幂等：按 (role_id, permission_id) / id 去重后补齐。
-- =============================================================================

-- ---------- ops_admin(1003)：推荐位 + 活动（V40）----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1003, t.permission_id, NULL, 0
FROM (
  SELECT 297301 AS id, 2190 AS permission_id UNION ALL
  SELECT 297302, 2191 UNION ALL
  SELECT 297303, 2192 UNION ALL
  SELECT 297304, 2193 UNION ALL
  SELECT 297305, 2194 UNION ALL
  SELECT 297306, 2195 UNION ALL
  SELECT 297307, 2196 UNION ALL
  SELECT 297308, 2197 UNION ALL
  SELECT 297309, 2198 UNION ALL
  SELECT 297310, 2199 UNION ALL
  SELECT 297311, 2200 UNION ALL
  SELECT 297312, 2201 UNION ALL
  SELECT 297313, 2202 UNION ALL
  SELECT 297314, 2203
) t
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.permission_id)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1003 AND rp.`permission_id` = t.permission_id AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.id
  );

-- ---------- audit_admin(1004)：重置用户密码（V32）----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1004, t.permission_id, NULL, 0
FROM (
  SELECT 297401 AS id, 2173 AS permission_id
) t
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.permission_id)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1004 AND rp.`permission_id` = t.permission_id AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.id
  );

-- ---------- security_admin(1005)：重置密码 + 设备封禁/绑定 + IP 限流 ----------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1005, t.permission_id, NULL, 0
FROM (
  SELECT 297501 AS id, 2173 AS permission_id UNION ALL
  SELECT 297502, 2182 UNION ALL
  SELECT 297503, 2183 UNION ALL
  SELECT 297504, 2184 UNION ALL
  SELECT 297505, 2185 UNION ALL
  SELECT 297506, 2205 UNION ALL
  SELECT 297507, 2206 UNION ALL
  SELECT 297508, 2207
) t
WHERE EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.permission_id)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = 1005 AND rp.`permission_id` = t.permission_id AND IFNULL(rp.`deleted`, 0) = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.`id` = t.id
  );

-- admin(1001) 已有通配权限 `*`，无需逐条补齐。