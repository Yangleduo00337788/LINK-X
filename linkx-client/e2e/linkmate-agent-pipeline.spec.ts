/**
 * 作者：yangleduo
 */
import { test, expect } from '@playwright/test'
import {
  resetHarnessNav,
  runPipeline,
  tomorrowDateKey,
  waitHarness
} from './agent-helpers'

test.describe('LinkMate Agent store pipeline (代操全链路)', () => {
  test.beforeEach(async ({ page }) => {
    await waitHarness(page)
    await resetHarnessNav(page, 'calendar')
  })

  test('pipeline: navigate → send_message with auto confirm', async ({ page }) => {
    const result = await runPipeline(page, [
      { name: 'navigate', arguments: { nav: 'chat' } },
      {
        name: 'send_message',
        arguments: { name: '张三', chatType: 'direct', content: 'Pipeline 你好' }
      }
    ])

    expect(result.phase).toBe('idle')
    expect(result.completed).toHaveLength(2)
    expect(result.completed.every(item => item.ok)).toBe(true)
    await expect(page.getByTestId('sent-messages')).toHaveText('Pipeline 你好')
    await expect(page.getByTestId('agent-bar')).toBeHidden()
  })

  test('pipeline: create_calendar_event shows confirm then auto approves', async ({ page }) => {
    const tomorrow = tomorrowDateKey()
    const result = await runPipeline(page, [
      {
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '明天下午', time: '14:00', endTime: '15:00' }
      }
    ])

    expect(result.completed).toHaveLength(1)
    expect(result.completed[0]?.ok).toBe(true)
    await expect(page.getByTestId('calendar-events')).toContainText(`周会@${tomorrow}`)
  })

  test('pipeline: manual confirm click before creating event', async ({ page }) => {
    const tomorrow = tomorrowDateKey()
    const resultPromise = runPipeline(
      page,
      [
        {
          name: 'create_calendar_event',
          arguments: { title: '手动确认周会', date: '明天', time: '14:00', endTime: '15:00' }
        }
      ],
      { manualConfirm: true }
    )

    await expect(page.getByTestId('agent-phase')).toHaveText('confirming', { timeout: 30000 })
    await expect(page.getByTestId('agent-bar')).toBeVisible()
    await page.getByTestId('agent-confirm').click()

    const result = await resultPromise
    expect(result.completed[0]?.ok).toBe(true)
    await expect(page.getByTestId('calendar-events')).toContainText(`手动确认周会@${tomorrow}`)
  })

  test('pipeline: reject confirm skips create_calendar_event', async ({ page }) => {
    const result = await runPipeline(
      page,
      [
        {
          name: 'create_calendar_event',
          arguments: { title: '应被跳过', date: '明天', time: '14:00', endTime: '15:00' }
        }
      ],
      { autoReject: true }
    )

    expect(result.completed[0]?.ok).toBe(false)
    await expect(page.getByTestId('calendar-events')).not.toContainText('应被跳过')
  })

  test('pipeline: open_search then add_favorite multi-step', async ({ page }) => {
    const result = await runPipeline(page, [
      { name: 'open_search', arguments: { keyword: '李四' } },
      { name: 'add_favorite', arguments: { title: 'Pipeline 收藏', content: '多步收藏内容' } }
    ])

    expect(result.completed).toHaveLength(2)
    expect(result.completed.every(item => item.ok)).toBe(true)
    await expect(page.getByTestId('search-result')).toHaveText('searched:李四')
    await expect(page.getByTestId('saved-notes')).toContainText('多步收藏内容')
  })
})
