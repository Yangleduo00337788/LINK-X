/**
 * 作者：yangleduo
 */
import { expect, test } from '@playwright/test'
import { loginClientContext, openClientLinkMate } from '../helpers/auth'
import { requireClientCredentials } from '../helpers/env'

interface LinkMateStatusMock {
  enabled?: boolean
  agentEnabled?: boolean
  model?: string
  dailyTokenLimit?: number
  dailyTokenUsed?: number
  deepThinkingSupported?: boolean
  voiceCallSupported?: boolean
}

async function mockLinkMateStatus(page: import('@playwright/test').Page, patch: LinkMateStatusMock) {
  await page.route('**/linkmate/status', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          enabled: true,
          model: 'deepseek-chat',
          dailyTokenLimit: 100000,
          dailyTokenUsed: 0,
          deepThinkingSupported: true,
          voiceCallSupported: false,
          agentEnabled: true,
          ...patch
        }
      })
    })
  })
}

test.describe('客户端灵伴 Agent 全局开关', () => {
  test.beforeEach(async ({ context }) => {
    requireClientCredentials()
    await loginClientContext(context)
  })

  test('管理员关闭 Agent 时代操按钮不可用并提示', async ({ page }) => {
    await mockLinkMateStatus(page, { enabled: true, agentEnabled: false })
    await openClientLinkMate(page)

    const agentButton = page.getByRole('button', { name: '代操' })
    await expect(agentButton).toHaveClass(/disabled/)

    await agentButton.click({ force: true })
    await expect(page.locator('.n-message').filter({ hasText: /管理员已关闭|disabled by the administrator/i })).toBeVisible()
  })

  test('管理员开启 Agent 时可切换代操模式', async ({ page }) => {
    await mockLinkMateStatus(page, { enabled: true, agentEnabled: true })
    await openClientLinkMate(page)

    const agentButton = page.getByRole('button', { name: '代操' })
    await expect(agentButton).not.toHaveClass(/disabled/)

    await agentButton.click()
    await expect(agentButton).toHaveClass(/active/)
    await expect(page.locator('.n-message').filter({ hasText: /已开启灵伴代操|agent mode enabled/i })).toBeVisible()

    await agentButton.click()
    await expect(agentButton).not.toHaveClass(/active/)
    await expect(page.locator('.n-message').filter({ hasText: /已关闭灵伴代操|agent mode disabled/i })).toBeVisible()
  })
})
