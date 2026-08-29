/**
 * 作者：yangleduo
 */
import { expect, test } from '@playwright/test'
import { loginAdminContext } from '../helpers/auth'
import { e2eEnv, requireAdminCredentials } from '../helpers/env'

test.describe('管理端登录', () => {
  test('登录后可打开控制台', async ({ page, context }) => {
    requireAdminCredentials()
    await loginAdminContext(context)
    const base = e2eEnv.adminBase.replace(/\/$/, '')
    await page.goto(`${base}/admin/dashboard`)
    await expect(page.getByText(/用户总数|用户总量|Total users/i).first()).toBeVisible({
      timeout: 30_000
    })
  })

  test('未登录访问后台会跳转登录页', async ({ page, context }) => {
    await context.clearCookies()
    const base = e2eEnv.adminBase.replace(/\/$/, '')
    await page.goto(`${base}/admin/dashboard`)
    await page.evaluate(() => {
      localStorage.clear()
      sessionStorage.clear()
    })
    await page.reload()
    await expect(page).toHaveURL(/\/login/, { timeout: 20_000 })
  })
})
