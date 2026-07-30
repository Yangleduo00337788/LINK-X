-- 为尚未绑定任何角色的用户补发默认「普通用户」角色（role_id=1002）
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_time`, `create_by`, `deleted`)
SELECT
    u.`id`,
    u.`id`,
    1002,
    NOW(),
    NULL,
    0
FROM `sys_user` u
WHERE IFNULL(u.`deleted`, 0) = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_user_role` ur WHERE ur.`user_id` = u.`id`
  )
  AND EXISTS (
      SELECT 1 FROM `sys_role` r WHERE r.`id` = 1002 AND r.`role_code` = 'user'
  );
