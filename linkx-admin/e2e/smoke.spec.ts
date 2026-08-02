import { test, expect } from '@playwright/test'
import { installAdminApiMocks } from './helpers/mockApi'

test.describe('Admin smoke', () => {
  test.beforeEach(async ({ page }) => {
    await installAdminApiMocks(page)
  })

  test('renders app shell', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('#app')).toBeVisible()
    await expect(page.locator('body')).toBeVisible()
  })

  test('serves page with admin title', async ({ page }) => {
    await page.goto('/login')
    await expect(page).toHaveTitle(/LinkX/i)
  })

  test('login page shows brand and form fields', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('.login-page')).toBeVisible({ timeout: 15000 })
    await expect(page.locator('.login-brand')).toBeVisible()
    await expect(page.locator('input[type="password"]').first()).toBeVisible()
  })

  test('unauthenticated visit to dashboard redirects to login', async ({ page }) => {
    await page.goto('/admin/dashboard')
    await expect(page).toHaveURL(/\/login/, { timeout: 15000 })
    await expect(page.locator('.login-page')).toBeVisible()
  })
})
