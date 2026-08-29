/**
 * 作者：yangleduo
 */
import type { APIRequestContext, BrowserContext, Page } from '@playwright/test'
import { request as playwrightRequest } from '@playwright/test'
import { e2eEnv } from './env'

interface ApiEnvelope<T> {
  code?: number
  data?: T
  message?: string
}

async function createApiContext(): Promise<APIRequestContext> {
  return playwrightRequest.newContext({
    baseURL: e2eEnv.apiBase.replace(/\/$/, ''),
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'X-Device-Id': 'e2e-playwright-device',
      'X-Device-Type': 'web'
    }
  })
}

async function applyStorageCookies(context: BrowserContext, origin: string, api: APIRequestContext) {
  const state = await api.storageState()
  const originUrl = new URL(origin)
  const cookies = state.cookies.map(cookie => ({
    name: cookie.name,
    value: cookie.value,
    domain: cookie.domain || originUrl.hostname,
    path: cookie.path || '/',
    expires: cookie.expires,
    httpOnly: cookie.httpOnly,
    secure: cookie.secure,
    sameSite: cookie.sameSite as 'Strict' | 'Lax' | 'None' | undefined
  }))
  if (cookies.length > 0) {
    await context.addCookies(cookies)
  }
}

export async function loginAdminContext(context: BrowserContext) {
  const api = await createApiContext()
  try {
    const res = await api.post('/api/admin/auth/login', {
      data: {
        username: e2eEnv.adminUser,
        password: e2eEnv.adminPassword
      }
    })
    if (!res.ok()) {
      throw new Error(`管理端登录失败: HTTP ${res.status()} ${await res.text()}`)
    }
    const body = (await res.json()) as ApiEnvelope<{ requiresTotp?: boolean; requiresTotpSetup?: boolean }>
    if (body.data?.requiresTotp || body.data?.requiresTotpSetup) {
      throw new Error('管理端账号开启了 TOTP，E2E 请使用未启用 2FA 的测试账号')
    }
    await applyStorageCookies(context, e2eEnv.adminBase, api)
  } finally {
    await api.dispose()
  }
}

export async function loginClientContext(context: BrowserContext) {
  const api = await createApiContext()
  try {
    const res = await api.post('/api/auth/login', {
      data: {
        username: e2eEnv.clientUser,
        password: e2eEnv.clientPassword
      }
    })
    if (!res.ok()) {
      throw new Error(`客户端登录失败: HTTP ${res.status()} ${await res.text()}`)
    }
    const body = (await res.json()) as ApiEnvelope<unknown>
    if (body.code !== 200) {
      throw new Error(`客户端登录失败: ${body.message || 'unknown error'}`)
    }
    await applyStorageCookies(context, e2eEnv.clientBase, api)
  } finally {
    await api.dispose()
  }
}

/** 通过登录页 UI 建立完整会话（Cookie + 前端状态），用于消息页等主界面用例 */
export async function loginClientViaUi(page: Page) {
  await page.goto('/#/')
  const passwordLink = page.getByRole('link', { name: /账密登录|password login/i })
  if (await passwordLink.isVisible().catch(() => false)) {
    await passwordLink.click()
  }
  await page.getByPlaceholder(/请输入账号|account/i).fill(e2eEnv.clientUser)
  await page.getByPlaceholder(/请输入密码|password/i).fill(e2eEnv.clientPassword)
  const terms = page.locator('.agreement-row__checkbox')
  if (!(await terms.isChecked().catch(() => true))) {
    await terms.check()
  }
  await page.getByRole('button', { name: /登\s*录|log\s*in/i }).click()
  await page.locator('.chat-list').first().waitFor({ state: 'visible', timeout: 60_000 })
}

export async function openAdminLinkMateSettings(page: Page) {
  const base = e2eEnv.adminBase.replace(/\/$/, '')
  await page.goto(`${base}/admin/settings?tab=linkmate`)
  await page.getByText(/允许 Agent 代操|Allow Agent mode/i).waitFor({ state: 'visible' })
}

export async function openClientLinkMate(page: Page) {
  await page.goto('/#/linkmate')
  await page.getByRole('button', { name: '代操' }).waitFor({ state: 'visible', timeout: 30_000 })
}
