import { test, expect } from '@playwright/test'
import { injectAdminSession, installAdminApiMocks } from './helpers/mockApi'

test.describe('Admin auth and navigation', () => {
  test.beforeEach(async ({ page }) => {
    await installAdminApiMocks(page)
  })

  test('password login navigates to dashboard', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 15000 })

    const userInput = page.locator('.login-page input').first()
    await userInput.fill('e2e_admin')
    await page.locator('input[type="password"]').first().fill('Test1234abcd')
    await page.getByRole('button', { name: /登\s*录|Log\s*in|Sign\s*in/i }).click()

    await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 15000 })
    await expect(page.locator('#app')).toBeVisible()
  })

  test('injected session can open users / devices / settings', async ({ page }) => {
    await injectAdminSession(page)

    for (const path of ['/admin/dashboard', '/admin/users', '/admin/devices', '/admin/settings']) {
      await page.goto(path)
      await expect(page).toHaveURL(new RegExp(path.replace('/', '\\/')), { timeout: 15000 })
      await expect(page.locator('#app')).toBeVisible()
      // 不应被踢回登录或 forbidden
      await expect(page).not.toHaveURL(/\/login/)
      await expect(page).not.toHaveURL(/\/forbidden/)
    }
  })
})
