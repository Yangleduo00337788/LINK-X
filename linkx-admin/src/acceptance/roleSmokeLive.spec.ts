/**
 * 生产/联调五角色 API 抽检（需真实后端 + 凭证环境变量）
 *
 * @see docs/testing/ADMIN_FIVE_ROLE_CHECKLIST.md
 */
import { describe, expect, it } from 'vitest'
import { ROLE_SMOKE_CASES, assertRoleSmoke, roleSmokeLabel } from './roleSmokeMatrix'

const API_BASE = (
  import.meta.env.ADMIN_API_BASE ||
  process.env.ADMIN_API_BASE ||
  'http://127.0.0.1:8080/api'
).replace(/\/$/, '')

type Cred = { roleCode: string; username: string; password: string }

const ROLE_ENV_KEYS: Record<string, [string, string]> = {
  super_admin: ['ADMIN_SMOKE_SUPER_USER', 'ADMIN_SMOKE_SUPER_PASS'],
  ops_admin: ['ADMIN_SMOKE_OPS_USER', 'ADMIN_SMOKE_OPS_PASS'],
  audit_admin: ['ADMIN_SMOKE_AUDIT_USER', 'ADMIN_SMOKE_AUDIT_PASS'],
  security_admin: ['ADMIN_SMOKE_SECURITY_USER', 'ADMIN_SMOKE_SECURITY_PASS'],
  readonly_observer: ['ADMIN_SMOKE_READONLY_USER', 'ADMIN_SMOKE_READONLY_PASS'],
}

const WRITE_PROBES: Record<
  string,
  { method: string; path: string; body?: Record<string, unknown> }
> = {
  ops_admin: {
    method: 'POST',
    path: '/admin/notices',
    body: { title: 'probe', content: 'x', targetSide: 'admin' },
  },
  audit_admin: {
    method: 'POST',
    path: '/admin/notices',
    body: { title: 'probe', content: 'x', targetSide: 'admin' },
  },
  security_admin: {
    method: 'POST',
    path: '/admin/feedback/1/reply',
    body: { content: 'probe' },
  },
  readonly_observer: {
    method: 'POST',
    path: '/admin/notices',
    body: { title: 'probe', content: 'x', targetSide: 'admin' },
  },
}

function parseCredentials(): Cred[] {
  const raw = process.env.ROLE_SMOKE_CREDENTIALS
  if (raw) return JSON.parse(raw) as Cred[]
  const out: Cred[] = []
  for (const [roleCode, [userKey, passKey]] of Object.entries(ROLE_ENV_KEYS)) {
    const username = process.env[userKey]
    const password = process.env[passKey]
    if (username && password) out.push({ roleCode, username, password })
  }
  return out
}

async function api<T = { code: number; data?: unknown; message?: string }>(
  method: string,
  path: string,
  token: string | null,
  body?: Record<string, unknown>
) {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = (await res.json()) as T & { code?: number }
  return { status: res.status, json }
}

async function login(username: string, password: string) {
  const { status, json } = await api<{ code: number; data: { accessToken: string } }>(
    'POST',
    '/admin/auth/login',
    null,
    { username, password }
  )
  expect(status, `login HTTP ${status}`).toBe(200)
  expect(json.code, JSON.stringify(json)).toBe(200)
  return json.data.accessToken
}

const creds = parseCredentials()
const liveEnabled = process.env.ROLE_SMOKE_LIVE === '1' || creds.length > 0

describe.skipIf(!liveEnabled)('五角色生产 API 抽检 @live', () => {
  if (!liveEnabled) {
    it.skip('skipped — set ROLE_SMOKE_LIVE=1 or credentials', () => {})
    return
  }

  for (const cred of creds) {
    const role = ROLE_SMOKE_CASES.find((r) => r.roleCode === cred.roleCode)
    it(`${role ? roleSmokeLabel(role.roleCode) : cred.roleCode} (${cred.roleCode})`, async () => {
      expect(role, `unknown roleCode ${cred.roleCode}`).toBeTruthy()

      const token = await login(cred.username, cred.password)

      const menusRes = await api<Array<{ name?: string; children?: unknown[] }>>(
        'GET',
        '/admin/auth/menus',
        token
      )
      const permsRes = await api<string[]>('GET', '/admin/auth/permissions', token)
      expect(menusRes.json.code).toBe(200)
      expect(permsRes.json.code).toBe(200)

      const menus = (menusRes.json.data || []) as Array<{ name?: string; children?: unknown[] }>
      const permissions = (permsRes.json.data || []) as string[]
      const failures = assertRoleSmoke(role!, menus, permissions)
      expect(failures, failures.join('; ')).toEqual([])

      const probe = WRITE_PROBES[cred.roleCode]
      if (probe) {
        const probeRes = await api(probe.method, probe.path, token, probe.body)
        expect(
          probeRes.status === 403 || probeRes.json.code === 403,
          `write probe ${probe.path} should 403`
        ).toBe(true)
      }

      if (cred.roleCode === 'super_admin') {
        const summary = await api('GET', '/admin/dashboard/summary', token)
        const settings = await api('GET', '/admin/settings', token)
        expect(summary.json.code).toBe(200)
        expect(settings.json.code).toBe(200)
      }
    })
  }
})
