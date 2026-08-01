/**
 * 前端角色冒烟矩阵（对齐后端 AdminRoleSmokeIT / 管理端开发文档 §37.4）
 *
 * - mustMenus: 登录后侧边栏应出现的菜单 name
 * - mustNotMenus: 不应出现的菜单 name
 * - allowPerms: 应具备的权限码（用于按钮/路由）
 * - denyPerms: 不应具备的权限码（写操作 / 敏感页）
 * - allowRoutes: 应可进入的前端 path
 * - denyRoutes: 应被守卫拦到 /forbidden 的 path
 */
export type RoleSmokeCase = {
  roleCode: string
  label: string
  mustMenus: string[]
  mustNotMenus: string[]
  allowPerms: string[]
  denyPerms: string[]
  allowRoutes: string[]
  denyRoutes: string[]
}

export const ROLE_SMOKE_CASES: RoleSmokeCase[] = [
  {
    roleCode: 'ops_admin',
    label: '运营管理员',
    mustMenus: ['dashboard', 'feedback', 'notices', 'statistics', 'recommends', 'activities'],
    mustNotMenus: ['settings', 'risk-event', 'devices'],
    allowPerms: [
      'admin:dashboard:view',
      'admin:feedback:reply',
      'admin:notice:create',
      'admin:user:export',
      'admin:recommend:list',
      'admin:activity:list',
    ],
    denyPerms: [
      'admin:setting:edit',
      'admin:user:freeze',
      'admin:risk-event:handle',
      'admin:risk-event:export',
      'admin:device:export',
      'admin:menu:create',
      'admin:role:assign-permission',
    ],
    allowRoutes: [
      '/admin/dashboard',
      '/admin/feedback',
      '/admin/notices',
      '/admin/statistics',
      '/admin/recommends',
      '/admin/activities',
    ],
    denyRoutes: ['/admin/settings', '/admin/risk-events', '/admin/devices', '/admin/menus'],
  },
  {
    roleCode: 'audit_admin',
    label: '审核管理员',
    mustMenus: ['review-task', 'risk-event', 'devices'],
    mustNotMenus: ['notices', 'statistics', 'settings'],
    allowPerms: ['admin:review:approve', 'admin:risk-event:handle', 'admin:user:freeze'],
    denyPerms: [
      'admin:notice:create',
      'admin:setting:edit',
      'admin:menu:edit',
      'admin:role:assign-permission',
    ],
    allowRoutes: ['/admin/reviews', '/admin/risk-events', '/admin/devices'],
    denyRoutes: ['/admin/notices', '/admin/statistics', '/admin/settings', '/admin/menus'],
  },
  {
    roleCode: 'security_admin',
    label: '安全管理员',
    mustMenus: ['risk-event', 'devices', 'blacklist'],
    mustNotMenus: ['feedback', 'notices', 'settings'],
    allowPerms: [
      'admin:risk-event:handle',
      'admin:device:kick',
      'admin:device:export',
      'admin:blacklist:list',
      'admin:blacklist:export',
    ],
    denyPerms: [
      'admin:feedback:reply',
      'admin:notice:create',
      'admin:menu:delete',
      'admin:role:assign-permission',
    ],
    allowRoutes: ['/admin/risk-events', '/admin/devices', '/admin/blacklist'],
    denyRoutes: ['/admin/feedback', '/admin/notices', '/admin/menus'],
  },
  {
    roleCode: 'readonly_observer',
    label: '只读观察员',
    mustMenus: ['dashboard', 'statistics', 'user', 'devices'],
    mustNotMenus: ['settings', 'blacklist', 'notices'],
    allowPerms: [
      'admin:dashboard:view',
      'admin:statistics:view',
      'admin:user:list',
      'admin:user:export',
      'admin:device:list',
      'admin:device:export',
    ],
    denyPerms: [
      'admin:device:kick',
      'admin:user:freeze',
      'admin:notice:create',
      'admin:setting:edit',
      'admin:risk-event:handle',
      'admin:blacklist:export',
      'admin:menu:reorder',
      'admin:role:assign-permission',
    ],
    allowRoutes: ['/admin/dashboard', '/admin/statistics', '/admin/users', '/admin/devices'],
    denyRoutes: ['/admin/settings', '/admin/blacklist', '/admin/notices', '/admin/menus'],
  },
]

/** 页面路由权限：与 router/index.ts meta.permission 对齐，供冒烟一致性校验 */
export const ADMIN_ROUTE_PERMISSIONS: Array<{ path: string; permission?: string; name: string }> = [
  { path: '/admin/dashboard', permission: 'admin:dashboard:view', name: 'Dashboard' },
  { path: '/admin/users', permission: 'admin:user:list', name: 'UserList' },
  { path: '/admin/users/:id', permission: 'admin:user:view', name: 'UserDetail' },
  { path: '/admin/blacklist', permission: 'admin:blacklist:list', name: 'Blacklist' },
  { path: '/admin/devices', permission: 'admin:device:list', name: 'Devices' },
  { path: '/admin/roles', permission: 'admin:role:list', name: 'RoleList' },
  { path: '/admin/permissions', permission: 'admin:permission:list', name: 'PermissionList' },
  { path: '/admin/menus', permission: 'admin:menu:list', name: 'MenuList' },
  { path: '/admin/depts', permission: 'admin:dept:list', name: 'DeptList' },
  { path: '/admin/audit-logs', permission: 'admin:audit:list', name: 'AuditLogs' },
  { path: '/admin/login-logs', permission: 'admin:login-log:list', name: 'LoginLogs' },
  { path: '/admin/risk-events', permission: 'admin:risk-event:list', name: 'RiskEvents' },
  { path: '/admin/rate-limits', permission: 'admin:rate-limit:list', name: 'RateLimits' },
  { path: '/admin/feedback', permission: 'admin:feedback:list', name: 'FeedbackList' },
  { path: '/admin/reviews', permission: 'admin:review:list', name: 'ReviewList' },
  { path: '/admin/sensitive-words', permission: 'admin:sensitive-word:list', name: 'SensitiveWordList' },
  { path: '/admin/notices', permission: 'admin:notice:list', name: 'Notices' },
  { path: '/admin/notice-inbox', permission: 'admin:notice:inbox', name: 'NoticeInbox' },
  { path: '/admin/banners', permission: 'admin:banner:list', name: 'Banners' },
  { path: '/admin/recommends', permission: 'admin:recommend:list', name: 'Recommends' },
  { path: '/admin/activities', permission: 'admin:activity:list', name: 'Activities' },
  { path: '/admin/settings', permission: 'admin:setting:view', name: 'Settings' },
  { path: '/admin/statistics', permission: 'admin:statistics:view', name: 'Statistics' },
  { path: '/admin/profile', name: 'Profile' },
]

export function hasPermission(permissions: string[], code?: string | string[]) {
  if (!code) return true
  if (permissions.includes('*')) return true
  const codes = Array.isArray(code) ? code : [code]
  return codes.some((c) => permissions.includes(c))
}

export function collectMenuNames(
  menus: Array<{ name?: string; children?: unknown[] }>,
  out: Set<string> = new Set(),
): Set<string> {
  for (const m of menus) {
    if (m.name) out.add(m.name)
    if (Array.isArray(m.children) && m.children.length) {
      collectMenuNames(m.children as Array<{ name?: string; children?: unknown[] }>, out)
    }
  }
  return out
}

export function assertRoleSmoke(
  role: RoleSmokeCase,
  menus: Array<{ name?: string; children?: unknown[] }>,
  permissions: string[],
) {
  const names = collectMenuNames(menus)
  const failures: string[] = []

  for (const m of role.mustMenus) {
    // review 菜单历史命名兼容
    if (m === 'review-task') {
      if (!names.has('review-task') && !names.has('review')) {
        failures.push(`missing menu: review-task|review`)
      }
      continue
    }
    if (!names.has(m)) failures.push(`missing menu: ${m}`)
  }
  for (const m of role.mustNotMenus) {
    if (names.has(m)) failures.push(`unexpected menu: ${m}`)
  }
  for (const p of role.allowPerms) {
    if (!hasPermission(permissions, p)) failures.push(`missing perm: ${p}`)
  }
  for (const p of role.denyPerms) {
    if (hasPermission(permissions, p)) failures.push(`unexpected perm: ${p}`)
  }
  for (const path of role.allowRoutes) {
    const meta = ADMIN_ROUTE_PERMISSIONS.find((r) => r.path === path)
    if (meta?.permission && !hasPermission(permissions, meta.permission)) {
      failures.push(`cannot enter route: ${path}`)
    }
  }
  for (const path of role.denyRoutes) {
    const meta = ADMIN_ROUTE_PERMISSIONS.find((r) => r.path === path)
    if (!meta?.permission) continue
    if (hasPermission(permissions, meta.permission)) {
      failures.push(`should deny route: ${path}`)
    }
  }

  return failures
}
