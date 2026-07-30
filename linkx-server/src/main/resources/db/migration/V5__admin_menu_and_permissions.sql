-- =============================================================================
-- V5: 管理端菜单 / 角色菜单关联 / 管理端权限码种子
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_admin_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID，根节点为0',
  `name` VARCHAR(64) NOT NULL COMMENT '菜单唯一标识',
  `title` VARCHAR(64) NOT NULL COMMENT '菜单展示名称',
  `path` VARCHAR(255) NOT NULL COMMENT '前端路由路径',
  `component` VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
  `redirect` VARCHAR(255) DEFAULT NULL COMMENT '重定向路径',
  `icon` VARCHAR(64) DEFAULT NULL COMMENT '图标名称',
  `menu_type` VARCHAR(16) NOT NULL DEFAULT 'menu' COMMENT 'dir/menu/button/api',
  `permission_code` VARCHAR(128) DEFAULT NULL COMMENT '权限码',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `hidden` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否隐藏',
  `cacheable` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否缓存',
  `external_link` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否外链',
  `keep_alive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否保活',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_admin_menu_name` (`name`),
  KEY `idx_sys_admin_menu_parent_id` (`parent_id`),
  KEY `idx_sys_admin_menu_sort_order` (`sort_order`),
  KEY `idx_sys_admin_menu_status` (`status`),
  KEY `idx_sys_admin_menu_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台菜单表';

CREATE TABLE IF NOT EXISTS `sys_admin_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_admin_role_menu` (`role_id`, `menu_id`),
  KEY `idx_sys_admin_role_menu_role_id` (`role_id`),
  KEY `idx_sys_admin_role_menu_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台角色菜单关联表';

-- 管理端菜单种子（固定 ID，便于角色绑定）
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(1,  0, 'dashboard',   '仪表盘',   '/admin/dashboard',  'views/DashboardView',   'Dashboard', 'menu', 'admin:dashboard:view', 1, 0, 1, 0, 1, 1),
(2,  0, 'user',        '用户管理', '/admin/users',      'views/UserListView',    'People',    'menu', 'admin:user:list',      2, 0, 1, 0, 1, 1),
(3,  0, 'rbac',        '权限中心', '/admin/rbac',       NULL,                    'Shield',    'dir',  NULL,                   3, 0, 1, 0, 1, 1),
(4,  3, 'role',        '角色管理', '/admin/roles',      'views/RoleListView',    'Badge',     'menu', 'admin:role:list',      1, 0, 1, 0, 1, 1),
(5,  3, 'permission',  '权限管理', '/admin/permissions','views/PermissionListView','Key',     'menu', 'admin:permission:list',2, 0, 1, 0, 1, 1),
(6,  3, 'menu',        '菜单管理', '/admin/menus',      'views/MenuListView',    'Menu',      'menu', 'admin:menu:list',      3, 0, 1, 0, 1, 1),
(7,  0, 'log',         '日志中心', '/admin/logs',       NULL,                    'History',   'dir',  NULL,                   4, 0, 1, 0, 1, 1),
(8,  7, 'audit-log',   '操作日志', '/admin/audit-logs', 'views/AuditLogView',    'Document',  'menu', 'admin:audit:list',     1, 0, 1, 0, 1, 1),
(9,  7, 'login-log',   '登录日志', '/admin/login-logs', 'views/LoginLogView',    'LogIn',     'menu', 'admin:login-log:list', 2, 0, 1, 0, 1, 1),
(10, 0, 'feedback',    '反馈管理', '/admin/feedback',   'views/FeedbackListView','Chatbox',   'menu', 'admin:feedback:list',  5, 0, 1, 0, 1, 1),
(11, 0, 'settings',    '系统配置', '/admin/settings',   'views/SettingView',     'Settings',  'menu', 'admin:setting:view',   6, 0, 1, 0, 1, 1),
(12, 0, 'versions',    '版本管理', '/admin/versions',   'views/VersionView',     'Cube',      'menu', 'admin:version:list',   7, 0, 1, 0, 1, 1);

-- admin 角色绑定全部菜单
INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 1),(1001, 2),(1001, 3),(1001, 4),(1001, 5),(1001, 6),
(1001, 7),(1001, 8),(1001, 9),(1001, 10),(1001, 11),(1001, 12);

-- 管理端权限码写入现有 sys_permission（admin 已有 *，可覆盖全部）
INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2101,'admin:dashboard:view','查看仪表盘','page','/admin/dashboard','仪表盘',1),
(2102,'admin:user:list','查看用户列表','page','/admin/users','用户管理',1),
(2103,'admin:user:view','查看用户详情','button',NULL,'用户详情',1),
(2104,'admin:user:edit','编辑用户资料','button',NULL,'用户编辑',1),
(2105,'admin:user:freeze','冻结用户','button',NULL,'冻结',1),
(2106,'admin:user:unfreeze','解冻用户','button',NULL,'解冻',1),
(2107,'admin:user:ban','封禁用户','button',NULL,'封禁',1),
(2108,'admin:user:unban','解封用户','button',NULL,'解封',1),
(2109,'admin:user:device:list','查看用户设备','button',NULL,'设备列表',1),
(2110,'admin:user:login:list','查看用户登录记录','button',NULL,'登录记录',1),
(2111,'admin:role:list','查看角色列表','page','/admin/roles','角色管理',1),
(2112,'admin:role:create','新增角色','button',NULL,'新增角色',1),
(2113,'admin:role:edit','编辑角色','button',NULL,'编辑角色',1),
(2114,'admin:role:delete','删除角色','button',NULL,'删除角色',1),
(2115,'admin:role:assign-menu','角色分配菜单','button',NULL,'菜单授权',1),
(2116,'admin:permission:list','查看权限列表','page','/admin/permissions','权限管理',1),
(2117,'admin:menu:list','查看菜单列表','page','/admin/menus','菜单管理',1),
(2118,'admin:menu:create','新增菜单','button',NULL,'新增菜单',1),
(2119,'admin:menu:edit','编辑菜单','button',NULL,'编辑菜单',1),
(2120,'admin:menu:delete','删除菜单','button',NULL,'删除菜单',1),
(2121,'admin:audit:list','查看操作日志','page','/admin/audit-logs','操作日志',1),
(2122,'admin:login-log:list','查看登录日志','page','/admin/login-logs','登录日志',1),
(2123,'admin:feedback:list','查看反馈列表','page','/admin/feedback','反馈管理',1),
(2124,'admin:feedback:reply','回复反馈','button',NULL,'回复反馈',1),
(2125,'admin:feedback:close','关闭反馈','button',NULL,'关闭反馈',1),
(2126,'admin:setting:view','查看系统配置','page','/admin/settings','系统配置',1),
(2127,'admin:setting:edit','编辑系统配置','button',NULL,'编辑配置',1),
(2128,'admin:version:list','查看版本列表','page','/admin/versions','版本管理',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2101,NULL),(1001,2102,NULL),(1001,2103,NULL),(1001,2104,NULL),
(1001,2105,NULL),(1001,2106,NULL),(1001,2107,NULL),(1001,2108,NULL),
(1001,2109,NULL),(1001,2110,NULL),(1001,2111,NULL),(1001,2112,NULL),
(1001,2113,NULL),(1001,2114,NULL),(1001,2115,NULL),(1001,2116,NULL),
(1001,2117,NULL),(1001,2118,NULL),(1001,2119,NULL),(1001,2120,NULL),
(1001,2121,NULL),(1001,2122,NULL),(1001,2123,NULL),(1001,2124,NULL),
(1001,2125,NULL),(1001,2126,NULL),(1001,2127,NULL),(1001,2128,NULL);
