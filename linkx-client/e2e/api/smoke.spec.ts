/**
 * 作者：yangleduo
 */
import { expect, test } from '@playwright/test'
import { request as playwrightRequest } from '@playwright/test'
import { e2eEnv } from '../helpers/env'

test.describe('API 冒烟', () => {
  test('health 与 auth config 可访问', async () => {
    const api = await playwrightRequest.newContext({
      baseURL: e2eEnv.apiBase.replace(/\/$/, '')
    })
    try {
      const health = await api.get('/api/health')
      expect(health.ok()).toBeTruthy()
      const healthBody = (await health.json()) as { code?: number; data?: { status?: string } }
      expect(healthBody.code).toBe(200)
      expect(healthBody.data?.status).toBe('UP')

      const authConfig = await api.get('/api/auth/config')
      expect(authConfig.ok()).toBeTruthy()
      const configBody = (await authConfig.json()) as { code?: number; data?: unknown }
      expect(configBody.code).toBe(200)
      expect(configBody.data).toBeTruthy()
    } finally {
      await api.dispose()
    }
  })

  test('admin auth config 可访问', async () => {
    const api = await playwrightRequest.newContext({
      baseURL: e2eEnv.apiBase.replace(/\/$/, '')
    })
    try {
      const res = await api.get('/api/admin/auth/config')
      expect(res.ok()).toBeTruthy()
      const body = (await res.json()) as { code?: number; data?: unknown }
      expect(body.code).toBe(200)
      expect(body.data).toBeTruthy()
    } finally {
      await api.dispose()
    }
  })
})
