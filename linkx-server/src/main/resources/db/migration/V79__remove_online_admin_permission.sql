-- 作者：yangleduo
-- 移除「在线管理员」功能相关权限
DELETE FROM sys_role_permission WHERE permission_id = 2277;
DELETE FROM sys_permission WHERE id = 2277;
