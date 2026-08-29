/**
 * 作者：yangleduo
 */
import { test, expect } from '@playwright/test'
import { resetHarnessNav, runAction, runPipeline, tomorrowDateKey, waitHarness } from './agent-helpers'

test.describe('LinkMate Agent core flows', () => {
  test.beforeEach(async ({ page }) => {
    await waitHarness(page)
    await resetHarnessNav(page, 'calendar')
  })

  test('send_message: types and sends via chat input', async ({ page }) => {
    const result = await runAction(page, 'send_message', {
      conversationId: '101',
      content: 'E2E消息'
    })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('sent-messages')).toContainText('E2E消息')
    await expect(page.getByTestId('current-nav')).toHaveText('chat')
  })

  test('create_calendar_event: adds event to calendar list', async ({ page }) => {
    const date = tomorrowDateKey()
    const result = await runAction(page, 'create_calendar_event', {
      title: '周会',
      date,
      time: '14:00',
      endTime: '15:00'
    })
    expect(result.uiHandled).toBe(true)
    await expect(page.getByTestId('current-nav')).toHaveText('calendar')
    await expect(page.getByTestId('calendar-events')).toContainText('周会')
  })

  test('pipeline: open chat then send message with auto confirm', async ({ page }) => {
    const result = await runPipeline(page, [
      { name: 'open_chat', arguments: { conversationId: '101' } },
      { name: 'send_message', arguments: { conversationId: '101', content: '流水线消息' } }
    ])
    expect(result.phase).toBe('idle')
    expect(result.completed.length).toBe(2)
    expect(result.completed.every(item => item.ok)).toBe(true)
    await expect(page.getByTestId('sent-messages')).toContainText('流水线消息')
  })
})
