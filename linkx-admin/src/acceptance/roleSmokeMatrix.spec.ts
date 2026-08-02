import { describe, expect, it } from 'vitest'
import {
  ADMIN_ROUTE_PERMISSIONS,
  ROLE_SMOKE_CASES,
  assertRoleSmoke,
  collectMenuNames,
  hasPermission,
} from './roleSmokeMatrix'

/** 与后端种子角色权限对齐的前端冒烟夹具（菜单 name + 权限码） */
const FIXTURES: Record<
  string,
  { menus: Array<{ name: string; children?: Array<{ name: string }> }>; permissions: string[] }
> = {
  super_admin: {
    menus: [
      { name: 'dashboard' },
      { name: 'settings' },
      {
        name: 'log',
        children: [{ name: 'risk-event' }, { name: 'rate-limit' }],
      },
      { name: 'notices' },
    ],
    permissions: ['*'],
  },
  ops_admin: {
    menus: [
      { name: 'dashboard' },
      { name: 'user' },
      { name: 'feedback' },
      {
        name: 'ops',
        children: [{ name: 'notices' }, { name: 'banners' }, { name: 'notice-inbox' }],
      },
      { name: 'statistics' },
      { name: 'recommends' },
      { name: 'activities' },
    ],
    permissions: [
      'admin:dashboard:view',
      'admin:user:list',
      'admin:user:view',
      'admin:feedback:list',
      'admin:feedback:reply',
      'admin:notice:list',
      'admin:notice:create',
      'admin:banner:list',
      'admin:statistics:view',
      'admin:user:export',
      'admin:recommend:list',
      'admin:activity:list',
    ],
  },
  audit_admin: {
    menus: [
      { name: 'dashboard' },
      { name: 'user' },
      {
        name: 'log',
        children: [{ name: 'audit-log' }, { name: 'login-log' }, { name: 'risk-event' }],
      },
      {
        name: 'review',
        children: [{ name: 'report-task' }, { name: 'review-task' }, { name: 'sensitive-word' }],
      },
      { name: 'blacklist' },
      { name: 'devices' },
    ],
    permissions: [
      'admin:dashboard:view',
      'admin:user:list',
      'admin:user:freeze',
      'admin:review:list',
      'admin:review:approve',
      'admin:risk-event:list',
      'admin:risk-event:handle',
      'admin:device:list',
      'admin:blacklist:list',
    ],
  },
  security_admin: {
    menus: [
      { name: 'dashboard' },
      { name: 'user' },
      {
        name: 'log',
        children: [
          { name: 'audit-log' },
          { name: 'login-log' },
          { name: 'risk-event' },
          { name: 'rate-limit' },
        ],
      },
      { name: 'blacklist' },
      { name: 'devices' },
    ],
    permissions: [
      'admin:dashboard:view',
      'admin:risk-event:list',
      'admin:risk-event:handle',
      'admin:device:list',
      'admin:device:kick',
      'admin:device:export',
      'admin:blacklist:list',
      'admin:blacklist:export',
      'admin:rate-limit:list',
      'admin:rate-limit:unblock',
      'admin:rate-limit:whitelist',
      'admin:audit:list',
      'admin:login-log:list',
    ],
  },
  readonly_observer: {
    menus: [
      { name: 'dashboard' },
      { name: 'user' },
      {
        name: 'log',
        children: [{ name: 'audit-log' }, { name: 'login-log' }, { name: 'risk-event' }],
      },
      {
        name: 'review',
        children: [{ name: 'review-task' }, { name: 'sensitive-word' }],
      },
      { name: 'devices' },
      { name: 'statistics' },
    ],
    permissions: [
      'admin:dashboard:view',
      'admin:statistics:view',
      'admin:user:list',
      'admin:user:export',
      'admin:device:list',
      'admin:device:export',
      'admin:audit:list',
      'admin:login-log:list',
      'admin:risk-event:list',
      'admin:review:list',
    ],
  },
}

describe('前端角色冒烟矩阵', () => {
  for (const role of ROLE_SMOKE_CASES) {
    it(`${role.label}(${role.roleCode})：菜单/权限/路由可见性符合预期`, () => {
      const fixture = FIXTURES[role.roleCode]
      expect(fixture, `missing fixture for ${role.roleCode}`).toBeTruthy()
      const failures = assertRoleSmoke(role, fixture.menus, fixture.permissions)
      expect(failures, failures.join('; ')).toEqual([])
    })
  }

  it('超管 * 权限可进入全部受保护路由', () => {
    for (const route of ADMIN_ROUTE_PERMISSIONS) {
      if (!route.permission) continue
      expect(hasPermission(['*'], route.permission)).toBe(true)
    }
  })

  it('受保护管理路由均声明 permission（profile 除外）', () => {
    const missing = ADMIN_ROUTE_PERMISSIONS.filter((r) => r.name !== 'Profile' && !r.permission)
    expect(missing).toEqual([])
  })

  it('菜单管理写权限对非超管角色均为 deny', () => {
    for (const role of ROLE_SMOKE_CASES) {
      if (role.roleCode === 'super_admin') continue
      expect(role.denyPerms.some((p) => p.startsWith('admin:menu:'))).toBe(true)
    }
  })

  it('collectMenuNames 递归收集菜单 name', () => {
    const names = collectMenuNames([
      { name: 'dashboard' },
      {
        name: 'log',
        children: [{ name: 'audit-log' }, { name: 'login-log' }],
      },
    ])
    expect([...names].sort()).toEqual(['audit-log', 'dashboard', 'log', 'login-log'])
  })

  it('hasPermission 支持 * 与数组权限码', () => {
    expect(hasPermission(['*'], 'admin:user:list')).toBe(true)
    expect(hasPermission(['admin:a', 'admin:b'], ['admin:b', 'admin:c'])).toBe(true)
    expect(hasPermission(['admin:a'], 'admin:b')).toBe(false)
    expect(hasPermission(['admin:a'], undefined)).toBe(true)
  })

  it('assertRoleSmoke 对缺失菜单返回失败原因', () => {
    const role = ROLE_SMOKE_CASES[0]
    const failures = assertRoleSmoke(role, [{ name: 'dashboard' }], role.allowPerms)
    expect(failures.some((f) => f.startsWith('missing menu:'))).toBe(true)
  })
})
