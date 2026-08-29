/**
 * 作者：yangleduo
 */
import { test, expect } from '@playwright/test'
import { resetHarnessNav, runAction, waitHarness } from './agent-helpers'

test.describe('LinkMate Agent flow variants', () => {
  test.beforeEach(async ({ page }) => {
    await waitHarness(page)
    await resetHarnessNav(page, 'calendar')
  })

  test('open_chat: by friend name 张三', async ({ page }) => {
    const result = await runAction(page, 'open_chat', { name: '张三', chatType: 'direct' })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('current-nav')).toHaveText('chat')
    await expect(page.locator('[data-lm-session-id="101"]')).toHaveClass(/active/)
  })

  test('send_message: by friend name 张三', async ({ page }) => {
    const result = await runAction(page, 'send_message', {
      name: '张三',
      chatType: 'direct',
      content: '按名称发送 E2E'
    })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('sent-messages')).toHaveText('按名称发送 E2E')
  })

  test('open_search: without keyword only opens modal', async ({ page }) => {
    const result = await runAction(page, 'open_search', {})
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('search-modal')).toHaveClass(/is-open/)
    await expect(page.getByTestId('comprehensive-search-input')).toHaveValue('')
    await expect(page.getByTestId('search-result')).toHaveText('')
  })

  test('navigate: to contacts panel', async ({ page }) => {
    const result = await runAction(page, 'navigate', { nav: 'contacts' })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('current-nav')).toHaveText('contacts')
    await expect(page.locator('[data-panel="contacts"]')).toHaveClass(/is-visible/)
  })

  test('open_chat: prefers direct chat over group containing name', async ({ page }) => {
    const result = await runAction(page, 'open_chat', { name: '张三', chatType: 'direct' })
    expect(result.uiHandled).toBe(true)
    await expect(page.locator('[data-lm-session-id="101"]')).toHaveClass(/active/)
    await expect(page.locator('[data-lm-session-id="202"]')).not.toHaveClass(/active/)
  })
})
