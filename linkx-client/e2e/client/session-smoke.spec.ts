/**
 * 作者：yangleduo
 */
import { expect, test } from '@playwright/test'
import { loginClientViaUi } from '../helpers/auth'
import { requireClientCredentials } from '../helpers/env'

test.describe('客户端已登录会话', () => {
  test.beforeEach(() => {
    requireClientCredentials()
  })

  test('登录后可进入消息页并看到会话列表', async ({ page }) => {
    await loginClientViaUi(page)
    await expect(page.locator('.chat-list').first()).toBeVisible()
  })
})

test.describe('客户端登录页', () => {
  test('未登录时可打开账密表单', async ({ page }) => {
    await page.goto('/#/')
    await expect(page.getByPlaceholder(/请输入账号|account/i)).toBeVisible({ timeout: 15_000 })
    await expect(page.getByPlaceholder(/请输入密码|password/i)).toBeVisible()
  })
})
