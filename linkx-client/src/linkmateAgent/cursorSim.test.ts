/**
 * 作者：yangleduo
 */
import { describe, expect, it, beforeEach } from 'vitest'
import { buildCursorSteps } from './cursorSim'
import type { LinkMateAgentAction } from './types'

function mountAgentDomHarness() {
  document.body.innerHTML = `
    <nav>
      <button data-lm-nav="chat">聊天</button>
      <button data-lm-nav="calendar">日历</button>
      <button data-lm-nav="favorites">收藏</button>
    </nav>
    <ul class="chat-list">
      <li data-lm-session-id="101" data-lm-session-name="张三">张三</li>
      <li data-lm-session-id="201" data-lm-session-name="项目群">项目群</li>
    </ul>
    <div class="message-input">
      <textarea data-lm-chat-input></textarea>
    </div>
    <button class="lx-btn--send" data-lm-send-btn>发送</button>
    <div class="panel-search-bar">
      <input class="search-input" data-lm-search-bar />
    </div>
    <button type="button" data-lm-calendar-add>新建日程</button>
    <button type="button" data-lm-favorites-add>新建笔记</button>
  `
}

describe('cursorSim', () => {
  beforeEach(() => {
    mountAgentDomHarness()
  })

  it('builds navigate steps targeting nav button', () => {
    const action: LinkMateAgentAction = {
      id: 'a1',
      name: 'navigate',
      arguments: { nav: 'calendar' }
    }
    const steps = buildCursorSteps(action)
    expect(steps.length).toBe(1)
    expect(steps[0].click).toBe(true)
    const btn = document.querySelector('[data-lm-nav="calendar"]')!
    const rect = btn.getBoundingClientRect()
    expect(steps[0].point.x).toBeCloseTo(rect.left + rect.width / 2, 0)
  })

  it('builds open_chat steps for session item', () => {
    const action: LinkMateAgentAction = {
      id: 'a2',
      name: 'open_chat',
      arguments: { conversationId: '101' }
    }
    const steps = buildCursorSteps(action)
    expect(steps.length).toBe(1)
    expect(steps[0].click).toBe(true)
  })

  it('builds send_message steps with input and send button', () => {
    const action: LinkMateAgentAction = {
      id: 'a3',
      name: 'send_message',
      arguments: { conversationId: '101', content: '测试消息' }
    }
    const steps = buildCursorSteps(action)
    expect(steps.length).toBeGreaterThanOrEqual(2)
    const typingStep = steps.find(s => s.typeText === '测试消息')
    expect(typingStep).toBeDefined()
    expect(steps[steps.length - 1].click).toBe(true)
  })

  it('builds open_search step targeting search bar', () => {
    const steps = buildCursorSteps({
      id: 'a4',
      name: 'open_search',
      arguments: { keyword: '张三' }
    })
    expect(steps.length).toBe(1)
    expect(steps[0].click).toBe(true)
  })

  it('builds create_calendar_event steps through calendar add button', () => {
    const steps = buildCursorSteps({
      id: 'a5',
      name: 'create_calendar_event',
      arguments: { title: '周会', date: '2026-08-28' }
    })
    expect(steps.length).toBe(2)
    expect(steps[0].click).toBe(true)
    expect(steps[1].click).toBe(true)
    const addBtn = document.querySelector('[data-lm-calendar-add]')!
    const rect = addBtn.getBoundingClientRect()
    expect(steps[1].point.x).toBeCloseTo(rect.left + rect.width / 2, 0)
  })

  it('builds add_favorite steps through favorites add button', () => {
    const steps = buildCursorSteps({
      id: 'a6',
      name: 'add_favorite',
      arguments: { title: '笔记' }
    })
    expect(steps.length).toBe(2)
    expect(steps[0].click).toBe(true)
    expect(steps[1].click).toBe(true)
    const addBtn = document.querySelector('[data-lm-favorites-add]')!
    const rect = addBtn.getBoundingClientRect()
    expect(steps[1].point.x).toBeCloseTo(rect.left + rect.width / 2, 0)
  })
})
