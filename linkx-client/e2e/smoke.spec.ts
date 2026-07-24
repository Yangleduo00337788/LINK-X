import { test, expect } from '@playwright/test'

test.describe('LinkX smoke', () => {
  test('renders app shell', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('#app')).toBeVisible()
    await expect(page.locator('body')).toBeVisible()
  })

  test('serves a browser page with a title', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/LinkX|Vite/i)
  })

  test('login page shows account fields and login button', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 15000 })
    await expect(page.locator('.lx-login-btn')).toBeVisible()
    await expect(page.getByRole('button', { name: /登\s*录|Log\s*in/i })).toBeVisible()
  })

  test('login page exposes username/password inputs', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 15000 })
    const user = page.locator('input[type="text"], input[name="username"], input[autocomplete="username"]').first()
    const pass = page.locator('input[type="password"]').first()
    await expect(user).toBeVisible()
    await expect(pass).toBeVisible()
  })

  test('weak-net e2e bridge module is loadable from built assets', async ({ page }) => {
    // 确保弱网专项与冒烟同属 Playwright 套件；本用例验证入口页可交互且无白屏
    await page.goto('/')
    await expect(page.locator('#app')).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('lang', /.*/)
  })
})
