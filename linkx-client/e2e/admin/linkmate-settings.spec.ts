/**
 * 作者：yangleduo
 */
import { expect, test } from '@playwright/test'
import { loginAdminContext, openAdminLinkMateSettings } from '../helpers/auth'
import { requireAdminCredentials } from '../helpers/env'

test.describe('管理端灵伴设置', () => {
  test.beforeEach(async ({ context }) => {
    requireAdminCredentials()
    await loginAdminContext(context)
  })

  test('模型输入实时显示深度思考推断结果', async ({ page }) => {
    await openAdminLinkMateSettings(page)

    const modelInput = page
      .locator('.n-form-item')
      .filter({ hasText: /模型名称|Model/i })
      .getByRole('textbox')
      .first()

    await modelInput.fill('deepseek-chat')
    await expect(page.getByText(/支持深度思考|Deep thinking supported/i)).toBeVisible()

    await modelInput.fill('gpt-4o-mini')
    await expect(page.getByText(/不支持深度思考|Deep thinking not supported/i)).toBeVisible()
  })

  test('展示 Agent 开关与群 AI 概览/默认策略区块', async ({ page }) => {
    await openAdminLinkMateSettings(page)

    await expect(page.getByText(/允许 Agent 代操|Allow Agent mode/i)).toBeVisible()
    await expect(page.getByText(/群 AI 功能概览|Group AI overview/i)).toBeVisible()
    await expect(page.getByText(/群聊总数|Total groups/i)).toBeVisible()
    await expect(page.getByText(/新建群默认策略|New group defaults/i)).toBeVisible()
    await expect(page.getByText(/默认开启群灵伴|Enable LinkMate by default/i)).toBeVisible()
    await expect(page.getByText(/默认开启主动发言|Enable proactive speak by default/i)).toBeVisible()
    await expect(page.getByText(/默认开启智能总结|Enable smart summary by default/i)).toBeVisible()
  })

  test('关闭灵伴时 Agent 开关不可操作', async ({ page }) => {
    await openAdminLinkMateSettings(page)

    const linkmateSwitch = page
      .locator('.n-form-item')
      .filter({ hasText: /启用灵伴|Enable LinkMate/i })
      .locator('.n-switch')
      .first()
    const agentSwitch = page
      .locator('.n-form-item')
      .filter({ hasText: /允许 Agent 代操|Allow Agent mode/i })
      .locator('.n-switch')
      .first()

    await linkmateSwitch.click()
    await expect(agentSwitch).toHaveClass(/n-switch--disabled/)
  })
})
