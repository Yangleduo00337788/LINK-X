-- =============================================================================
-- V31: 补齐 readonly_observer 权限点（修复 V30 部分环境未落库）
-- =============================================================================

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `create_by`, `deleted`)
SELECT t.id, 1006, t.permission_id, NULL, 0
FROM (
  SELECT 296101 AS id, 2101 AS permission_id UNION ALL
  SELECT 296102, 2102 UNION ALL
  SELECT 296103, 2103 UNION ALL
  SELECT 296109, 2109 UNION ALL
  SELECT 296110, 2110 UNION ALL
  SELECT 296153, 2153 UNION ALL
  SELECT 296121, 2121 UNION ALL
  SELECT 296122, 2122 UNION ALL
  SELECT 296168, 2168 UNION ALL
  SELECT 296169, 2169 UNION ALL
  SELECT 296146, 2146 UNION ALL
  SELECT 296147, 2147 UNION ALL
  SELECT 296156, 2156 UNION ALL
  SELECT 296123, 2123 UNION ALL
  SELECT 296129, 2129 UNION ALL
  SELECT 296155, 2155 UNION ALL
  SELECT 296143, 2143 UNION ALL
  SELECT 296144, 2144 UNION ALL
  SELECT 296145, 2145 UNION ALL
  SELECT 296171, 2171
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` rp
  WHERE rp.role_id = 1006 AND rp.permission_id = t.permission_id AND rp.deleted = 0
)
AND NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` rp2 WHERE rp2.id = t.id
);
