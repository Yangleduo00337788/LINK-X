/**
 * 作者：yangleduo
 */
import { test, expect } from '@playwright/test'
import { resetHarnessNav, runAction, waitHarness } from './agent-helpers'

test.describe('LinkMate Agent extended tools', () => {
  test.beforeEach(async ({ page }) => {
    await waitHarness(page)
    await resetHarnessNav(page, 'calendar')
  })

  test('open_contacts: navigates to contacts panel', async ({ page }) => {
    const result = await runAction(page, 'open_contacts', { view: 'default' })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('current-nav')).toHaveText('contacts')
    await expect(page.locator('[data-panel="contacts"]')).toHaveClass(/is-visible/)
  })

  test('navigate contacts: same as open_contacts cursor path', async ({ page }) => {
    const result = await runAction(page, 'navigate', { nav: 'contacts' })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('current-nav')).toHaveText('contacts')
    await expect(page.locator('[data-panel="contacts"]')).toHaveClass(/is-visible/)
  })

  test('open_linkmate: opens linkmate panel via add menu', async ({ page }) => {
    const result = await runAction(page, 'open_linkmate', {})
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('current-nav')).toHaveText('chat')
    await expect(page.getByTestId('linkmate-panel')).toHaveClass(/is-open/)
  })

  test('navigate linkmate: same as open_linkmate cursor path', async ({ page }) => {
    const result = await runAction(page, 'navigate', { nav: 'linkmate' })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('linkmate-panel')).toHaveClass(/is-open/)
  })
})
