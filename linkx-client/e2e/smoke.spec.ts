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
})
