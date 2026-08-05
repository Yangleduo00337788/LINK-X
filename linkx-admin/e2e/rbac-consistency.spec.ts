/**
 * 五角色 RBAC 前后端一致性抽检（对接真实后端 + 浏览器）
 * 运行：cd linkx-admin && npx playwright test e2e/rbac-consistency.spec.ts --config=playwright.live.config.ts
 */
import { test, expect, type Page } from '@playwright/test'
import { ROLE_SMOKE_CASES, assertRoleSmoke, collectMenuNames } from '../src/acceptance/roleSmokeMatrix'

const API_BASE = (process.env.ADMIN_API_BASE || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const ADMIN_BASE = (process.env.ADMIN_WEB_BASE || 'http://127.0.0.1:5174').replace(/\/$/, '')

type Cred = { roleCode: string; username: string; password: string }

const DEFAULT_CREDS: Cred[] = [
  { roleCode: 'super_admin', username: 'admin', password: 'Test1234abcd' },
  { roleCode: 'ops_admin', username: 'ops_admin', password: 'Test1234abcd' },
  { roleCode: 'audit_admin', username: 'audit_admin', password: 'Test1234abcd' },
  { roleCode: 'security_admin', username: 'security_admin', password: 'Test1234abcd' },
  { roleCode: 'readonly_observer', username: 'readonly_observer', password: 'Test1234abcd' },
]

function parseCredentials(): Cred[] {
  const raw = process.env.ROLE_SMOKE_CREDENTIALS
  if (raw) return JSON.parse(raw) as Cred[]
  const out: Cred[] = []
  for (const c of DEFAULT_CREDS) {
    const userKey = `ADMIN_SMOKE_${c.roleCode.toUpperCase().replace('ADMIN', '').replace('_', '')}_USER`
    const passKey = userKey.replace('_USER', '_PASS')
    const username = process.env[userKey] || c.username
    const password = process.env.ADMIN_SMOKE_PASS || process.env[passKey] || c.password
    out.push({ roleCode: c.roleCode, username, password })
  }
  return out
}

async function apiLogin(username: string, password: string) {
  const res = await fetch(`${API_BASE}/admin/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const json = (await res.json()) as { code: number; data?: { accessToken?: string; requiresTotp?: boolean }; message?: string }
  return { status: res.status, json }
}

async function apiGet<T>(path: string, token: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return (await res.json()) as { code: number; data?: T; message?: string }
}

async function loginViaUi(page: Page, username: string, password: string) {
  await page.goto(`${ADMIN_BASE}/login`)
  await expect(page.getByRole('textbox', { name: /管理员账号|username/i })).toBeVisible({ timeout: 15000 })
  await page.getByRole('textbox', { name: /管理员账号|username/i }).fill(username)
  await page.getByRole('textbox', { name: /密码|password/i }).fill(password)
  await page.getByRole('button', { name: /登\s*录|Log\s*in|Sign\s*in/i }).click()
  await page.waitForTimeout(2500)
  if (page.url().includes('/login')) {
    throw new Error(`login failed for ${username}`)
  }
}

async function readFrontendPermissions(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const raw = localStorage.getItem('linkx-admin-auth-v2')
    if (!raw) return []
    try {
      const parsed = JSON.parse(raw) as { permissions?: string[] }
      return parsed.permissions || []
    } catch {
      return []
    }
  })
}

async function fetchMenusFromPage(page: Page, token: string) {
  return page.evaluate(
    async ({ apiBase, bearer }) => {
      const res = await fetch(`${apiBase}/admin/auth/menus`, {
        headers: { Authorization: `Bearer ${bearer}` },
      })
      const json = (await res.json()) as { data?: Array<{ name?: string; children?: unknown[] }> }
      return json.data || []
    },
    { apiBase: API_BASE, bearer: token }
  )
}

const creds = parseCredentials()

test.describe('五角色 RBAC 前后端一致性 @live', () => {
  for (const cred of creds) {
    const role = ROLE_SMOKE_CASES.find((r) => r.roleCode === cred.roleCode)
    const label = cred.roleCode

    test(`${label} (${cred.username}) API 权限与矩阵一致`, async () => {
      const login = await apiLogin(cred.username, cred.password)
      expect(login.status, JSON.stringify(login.json)).toBe(200)
      expect(login.json.code, login.json.message || JSON.stringify(login.json)).toBe(200)
      expect(login.json.data?.accessToken, 'missing token').toBeTruthy()
      if (login.json.data?.requiresTotp) {
        test.skip(true, 'requires TOTP')
        return
      }
      const token = login.json.data!.accessToken!

      const menusRes = await apiGet<Array<{ name?: string; children?: unknown[] }>>('/admin/auth/menus', token)
      const permsRes = await apiGet<string[]>('/admin/auth/permissions', token)
      expect(menusRes.code).toBe(200)
      expect(permsRes.code).toBe(200)

      const menus = menusRes.data || []
      const permissions = permsRes.data || []
      expect(role, `unknown role ${cred.roleCode}`).toBeTruthy()
      const failures = assertRoleSmoke(role!, menus, permissions)
      expect(failures, failures.join('; ')).toEqual([])
    })

    test(`${label} (${cred.username}) 浏览器权限与 API 一致`, async ({ page }) => {
      const login = await apiLogin(cred.username, cred.password)
      expect(login.json.code).toBe(200)
      if (!login.json.data?.accessToken) return

      await loginViaUi(page, cred.username, cred.password)
      await page.waitForURL(/\/admin\//, { timeout: 15000 })

      const apiPerms = (await apiGet<string[]>('/admin/auth/permissions', login.json.data.accessToken)).data || []
      const apiMenus = (await apiGet<Array<{ name?: string; children?: unknown[] }>>('/admin/auth/menus', login.json.data.accessToken)).data || []

      const fePerms = await readFrontendPermissions(page)

      const apiPermSet = new Set(apiPerms)
      const fePermSet = new Set(fePerms)
      const missingInFe = apiPerms.filter((p) => !fePermSet.has(p) && p !== '*')
      const extraInFe = fePerms.filter((p) => !apiPermSet.has(p) && p !== '*')
      expect(missingInFe, `frontend missing perms: ${missingInFe.join(', ')}`).toEqual([])
      expect(extraInFe, `frontend extra perms: ${extraInFe.join(', ')}`).toEqual([])

      const feMenus = await fetchMenusFromPage(page, login.json.data.accessToken)
      const apiMenuNames = [...collectMenuNames(apiMenus)]
      const feMenuNames = [...collectMenuNames(feMenus)]
      expect(feMenuNames.sort()).toEqual(apiMenuNames.sort())

      // 审核/审批路由守卫抽检
      const reviewRoutes = ['/admin/reviews', '/admin/approval-inbox', '/admin/approval-flows']
      for (const path of reviewRoutes) {
        await page.goto(`${ADMIN_BASE}${path}`)
        await page.waitForTimeout(800)
        const hasReviewPerm = apiPerms.includes('*') || apiPerms.includes('admin:review:list')
        const hasApprovalInbox = apiPerms.includes('*') || apiPerms.includes('admin:approval:inbox')
        const hasApprovalFlow = apiPerms.includes('*') || apiPerms.includes('admin:approval-flow:list')
        const shouldAllow =
          (path.includes('reviews') && hasReviewPerm) ||
          (path.includes('approval-inbox') && hasApprovalInbox) ||
          (path.includes('approval-flows') && hasApprovalFlow)
        if (shouldAllow) {
          expect(page.url()).not.toContain('/forbidden')
          expect(page.url()).not.toContain('/login')
        } else {
          expect(page.url()).toMatch(/\/forbidden|\/admin\/dashboard/)
        }
      }
    })
  }
})
