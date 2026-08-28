/**
 * 作者：yangleduo
 */
import { describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { parseLinkMateSseEvent } from '../../utils/linkmateSse'
import { useLinkMateAgentStore } from '../../stores/linkmateAgent'
import { parseAgentAction } from '../types'

describe('linkmateAgent integration pipeline', () => {
  it('SSE done with actions → store parse → executable actions', () => {
    setActivePinia(createPinia())
    const agentStore = useLinkMateAgentStore()
    const onDone = vi.fn()

    const ssePayload = {
      messageId: '99',
      sessionId: '1',
      totalTokens: '120',
      actions: [
        {
          id: 'call_nav',
          name: 'navigate',
          arguments: '{"nav":"calendar"}'
        },
        {
          id: 'call_msg',
          name: 'send_message',
          arguments: '{"name":"张三","content":"你好","chatType":"direct"}'
        }
      ]
    }

    parseLinkMateSseEvent(`event: done\ndata: ${JSON.stringify(ssePayload)}\n\n`, {
      onDone: payload => onDone(payload)
    })

    expect(onDone).toHaveBeenCalledOnce()
    const payload = onDone.mock.calls[0][0]
    expect(payload.actions).toHaveLength(2)

    const parsed = agentStore.parseActionsFromPayload(payload.actions)
    expect(parsed).toHaveLength(2)
    expect(parsed[0].name).toBe('navigate')
    expect(parsed[1].name).toBe('send_message')
    expect(parsed[1].arguments).toEqual({
      name: '张三',
      content: '你好',
      chatType: 'direct'
    })

    for (const action of parsed) {
      expect(parseAgentAction(action)).not.toBeNull()
    }
  })

  it('filters invalid actions from mixed payload', () => {
    setActivePinia(createPinia())
    const agentStore = useLinkMateAgentStore()

    const parsed = agentStore.parseActionsFromPayload([
      { id: 'ok', name: 'open_calendar', arguments: {} },
      { id: 'bad', name: 'unknown_tool', arguments: {} },
      { name: 'navigate', arguments: { nav: 'chat' } },
      null,
      { id: 'empty', name: '', arguments: {} }
    ])

    expect(parsed).toHaveLength(2)
    expect(parsed.map(a => a.name)).toEqual(['open_calendar', 'navigate'])
  })

  it('SSE tool_call → planning preview in agent store', () => {
    setActivePinia(createPinia())
    const agentStore = useLinkMateAgentStore()

    agentStore.beginPlanning()
    agentStore.previewToolCall({
      id: 'call_nav',
      name: 'navigate',
      arguments: '{"nav":"calendar"}'
    })
    agentStore.previewToolCall({
      id: 'call_msg',
      name: 'send_message',
      arguments: '{"name":"张三","content":"你好","chatType":"direct"}'
    })

    expect(agentStore.run.phase).toBe('planning')
    expect(agentStore.run.plannedActions).toHaveLength(2)
    expect(agentStore.run.plannedActions[0].name).toBe('navigate')
    expect(agentStore.run.plannedActions[1].name).toBe('send_message')

    agentStore.previewToolCall({
      id: 'call_msg',
      name: 'send_message',
      arguments: '{"name":"张三","content":"更新内容","chatType":"direct"}'
    })
    expect(agentStore.run.plannedActions).toHaveLength(2)
    expect(agentStore.run.plannedActions[1].arguments.content).toBe('更新内容')

    agentStore.clearPlanning()
    expect(agentStore.run.phase).toBe('idle')
    expect(agentStore.run.plannedActions).toHaveLength(0)
  })

  it('simulates linkmate API onDone action extraction', () => {
    const payload = {
      sessionId: '42',
      messageId: '88',
      totalTokens: '50',
      actions: [
        { id: 't1', name: 'add_favorite', arguments: '{"title":"测试"}' }
      ]
    }

    let extractedActions: Array<{ id: string; name: string; arguments: string }> | undefined
    parseLinkMateSseEvent(`event: done\ndata: ${JSON.stringify(payload)}\n\n`, {
      onDone: p => {
        if (Array.isArray(p.actions)) {
          extractedActions = p.actions
            .filter((item): item is Record<string, unknown> => item != null && typeof item === 'object')
            .map(item => ({
              id: String(item.id ?? ''),
              name: String(item.name ?? ''),
              arguments: String(item.arguments ?? '')
            }))
            .filter(item => item.id && item.name)
        }
      }
    })

    expect(extractedActions).toEqual([
      { id: 't1', name: 'add_favorite', arguments: '{"title":"测试"}' }
    ])
  })
})
