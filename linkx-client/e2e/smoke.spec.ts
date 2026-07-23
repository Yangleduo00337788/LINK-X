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
})
