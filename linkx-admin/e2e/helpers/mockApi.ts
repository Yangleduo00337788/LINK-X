import type { Page } from '@playwright/test'

/** 超级管理员权限（覆盖关键冒烟路由） */
const SUPER_PERMS = [
  'admin:dashboard:view',
  'admin:user:list',
  'admin:user:view',
  'admin:device:list',
  'admin:setting:view',
  '*',
]

const MOCK_USER = {
  id: 1,
  username: 'e2e_admin',
  nickname: 'E2E Admin',
  avatar: '',
  status: 1,
  permissions: SUPER_PERMS,
}

const MOCK_MENUS = [
  {
    id: 1,
    name: 'dashboard',
    path: '/admin/dashboard',
    title: '仪表盘',
    children: [],
  },
  {
    id: 2,
    name: 'users',
    path: '/admin/users',
    title: '用户管理',
    children: [],
  },
  {
    id: 3,
    name: 'devices',
    path: '/admin/devices',
    title: '设备管理',
    children: [],
  },
  {
    id: 4,
    name: 'settings',
    path: '/admin/settings',
    title: '系统设置',
    children: [],
  },
]

function ok(data: unknown) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'ok', data }),
  }
}

/**
 * 拦截 /api/**，使管理端可在无真实后端时完成登录后导航冒烟。
 */
export async function installAdminApiMocks(page: Page) {
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace(/^\/api/, '') || '/'
    const method = req.method().toUpperCase()

    if (path === '/admin/auth/config' && method === 'GET') {
      return route.fulfill(
        ok({
          captchaEnabled: false,
          totpEnabled: false,
          registerEnabled: false,
        })
      )
    }

    if (path === '/admin/auth/login' && method === 'POST') {
      return route.fulfill(
        ok({
          accessToken: 'e2e-access-token',
          refreshToken: 'e2e-refresh-token',
          requiresTotp: false,
          requiresTotpSetup: false,
          user: MOCK_USER,
        })
      )
    }

    if (path === '/admin/auth/me' && method === 'GET') {
      return route.fulfill(ok(MOCK_USER))
    }

    if (path === '/admin/auth/menus' && method === 'GET') {
      return route.fulfill(ok(MOCK_MENUS))
    }

    if (path === '/admin/auth/permissions' && method === 'GET') {
      return route.fulfill(ok(SUPER_PERMS))
    }

    if (path === '/admin/auth/refresh' && method === 'POST') {
      return route.fulfill(
        ok({
          accessToken: 'e2e-access-token-refreshed',
          refreshToken: 'e2e-refresh-token',
        })
      )
    }

    if (path.startsWith('/admin/users') && method === 'GET') {
      return route.fulfill(
        ok({
          records: [],
          total: 0,
          page: 1,
          size: 20,
        })
      )
    }

    if (path.startsWith('/admin/devices') && method === 'GET') {
      return route.fulfill(
        ok({
          records: [],
          total: 0,
          page: 1,
          size: 20,
        })
      )
    }

    if (path === '/admin/settings' && method === 'PUT') {
      const body = route.request().postDataJSON() as Record<string, unknown> | null
      return route.fulfill(
        ok({
          register: body?.register ?? { registerEnabled: true, forgotPasswordEmailEnabled: true },
          login: body?.login ?? {},
          password: body?.password ?? {},
          client: body?.client ?? {},
          mail: body?.mail ?? {},
        })
      )
    }

    if (path.startsWith('/admin/settings') && method === 'GET') {
      return route.fulfill(
        ok({
          auth: { captchaEnabled: false },
          mail: {},
          storage: {},
        })
      )
    }

    if (path.startsWith('/admin/dashboard') && method === 'GET') {
      return route.fulfill(
        ok({
          userTotal: 0,
          onlineUsers: 0,
          todayMessages: 0,
        })
      )
    }

    if (path.includes('/admin/events/stream')) {
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: '',
      })
    }

    // 默认成功空数据，避免未 mock 接口拖垮页面
    return route.fulfill(ok(null))
  })
}

/** 注入已登录 token，跳过登录表单。 */
export async function injectAdminSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('linkx_admin_access_token', 'e2e-access-token')
    localStorage.setItem('linkx_admin_refresh_token', 'e2e-refresh-token')
  })
}
