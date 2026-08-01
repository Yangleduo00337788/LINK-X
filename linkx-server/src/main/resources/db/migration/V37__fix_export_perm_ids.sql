-- =============================================================================
-- V37: 修复 V35 与 V33 的权限 ID 冲突
-- V33 已占用 2174 = admin:menu:reorder；V35 误用 2174/2175 写入导出权限（INSERT IGNORE 时设备导出未真正落地）
-- 正确 ID：2176=admin:device:export，2177=admin:blacklist:export
-- =============================================================================

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2176,'admin:device:export','导出设备列表','button',NULL,'设备会话导出',1),
(2177,'admin:blacklist:export','导出黑名单','button',NULL,'黑名单导出',1);

-- 若 V35 已用 2175 成功写入 blacklist:export，则删除重复编码的 2177，统一用已有行
DELETE FROM `sys_permission`
WHERE `id` = 2177
  AND EXISTS (
    SELECT 1 FROM (
      SELECT `id` FROM `sys_permission`
      WHERE `permission_code` = 'admin:blacklist:export' AND `id` <> 2177
    ) t
  );

-- 设备导出：把误绑到 2174(menu:reorder) 的角色授权改到真正的 device:export
UPDATE `sys_role_permission` rp
INNER JOIN `sys_permission` p ON p.`permission_code` = 'admin:device:export'
SET rp.`permission_id` = p.`id`
WHERE rp.`id` IN (294174, 295174, 296174);

-- 超管补齐设备/黑名单导出（按 permission_code 解析真实 ID）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`)
SELECT 1001, p.`id`, NULL
FROM `sys_permission` p
WHERE p.`permission_code` IN ('admin:device:export', 'admin:blacklist:export');

-- 审核 / 安全：黑名单导出（若 V35 的 294175/295175 已指向正确 blacklist:export 则保持）
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, t.role_id, p.id, NULL, 0
FROM (
  SELECT 294177 AS id, 1004 AS role_id UNION ALL
  SELECT 295177, 1005
) t
INNER JOIN `sys_permission` p ON p.`permission_code` = 'admin:blacklist:export'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` rp
  WHERE rp.`role_id` = t.role_id AND rp.`permission_id` = p.`id` AND rp.`deleted` = 0
);
