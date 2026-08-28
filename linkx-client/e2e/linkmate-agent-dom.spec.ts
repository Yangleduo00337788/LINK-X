/**
 * 作者：yangleduo
 */
import { test, expect } from '@playwright/test'

test.describe('LinkMate Agent DOM harness', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/agent-dom-harness.html')
    await expect(page.getByTestId('harness-title')).toBeVisible()
  })

  test('nav hooks are discoverable for cursor simulation', async ({ page }) => {
    await expect(page.locator('[data-lm-nav="chat"]')).toBeVisible()
    await expect(page.locator('[data-lm-nav="calendar"]')).toBeVisible()
    await expect(page.locator('[data-lm-nav="favorites"]')).toBeVisible()
  })

  test('session list hooks resolve by id and name', async ({ page }) => {
    const byId = page.locator('[data-lm-session-id="101"]')
    await expect(byId).toBeVisible()
    await expect(byId).toHaveAttribute('data-lm-session-name', '张三')

    const byName = page.locator('[data-lm-session-name="项目群"]')
    await expect(byName).toHaveAttribute('data-lm-session-id', '201')
  })

  test('chat input and send button hooks exist', async ({ page }) => {
    await expect(page.getByTestId('chat-input')).toBeVisible()
    await expect(page.getByTestId('send-btn')).toBeVisible()
  })

  test('search bar hook exists for open_search action', async ({ page }) => {
    await expect(page.getByTestId('search-bar')).toBeVisible()
  })

  test('calendar and favorites add hooks exist', async ({ page }) => {
    await expect(page.getByTestId('calendar-add')).toBeVisible()
    await expect(page.getByTestId('favorites-add')).toBeVisible()
  })

  test('simulated navigate click switches focus target', async ({ page }) => {
    const calendarBtn = page.locator('[data-lm-nav="calendar"]')
    await calendarBtn.click()
    await expect(calendarBtn).toBeFocused()
  })

  test('simulated send_message flow fills input', async ({ page }) => {
    await page.locator('[data-lm-session-id="101"]').click()
    const input = page.getByTestId('chat-input')
    await input.fill('E2E 测试消息')
    await expect(input).toHaveValue('E2E 测试消息')
    await page.getByTestId('send-btn').click()
  })
})
