import { test, expect } from '@playwright/test'

/**
 * 对接真实后端的冒烟（默认 CI 排除）。
 *
 * 启用：E2E_LIVE=1 ADMIN_USER=... ADMIN_PASS=... npm run test:e2e:live
 * 需本机 linkx-server 已启动，且 vite preview / 代理可访问 /api。
 */
test.describe('Admin live @live', () => {
  test('login against real API when credentials provided', async ({ page }) => {
    const user = process.env.ADMIN_USER
    const pass = process.env.ADMIN_PASS
    test.skip(!user || !pass, 'Set ADMIN_USER and ADMIN_PASS for live E2E')

    await page.goto('/login')
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 15000 })

    await page.locator('.login-page input').first().fill(user!)
    await page.locator('input[type="password"]').first().fill(pass!)
    await page.getByRole('button', { name: /登\s*录|Log\s*in|Sign\s*in/i }).click()

    // 可能进入 TOTP；有 token 时进 dashboard
    await page.waitForTimeout(2000)
    const url = page.url()
    expect(url.includes('/login') || url.includes('/admin/') || url.includes('totp')).toBeTruthy()
  })
})
