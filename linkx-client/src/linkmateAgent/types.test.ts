/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import {
  isNavKey,
  LINKMATE_AGENT_TOOL_NAMES,
  parseAgentAction,
  VALID_NAV_KEYS
} from './types'

describe('linkmateAgent types', () => {
  it('recognizes valid nav keys', () => {
    for (const key of VALID_NAV_KEYS) {
      expect(isNavKey(key)).toBe(true)
    }
    expect(isNavKey('invalid')).toBe(false)
    expect(isNavKey(null)).toBe(false)
  })

  it('parses action with object arguments', () => {
    const action = parseAgentAction({
      id: 'call_1',
      name: 'navigate',
      arguments: { nav: 'calendar' }
    })
    expect(action).toEqual({
      id: 'call_1',
      name: 'navigate',
      arguments: { nav: 'calendar' }
    })
  })

  it('parses action with JSON string arguments', () => {
    const action = parseAgentAction({
      id: 'call_2',
      name: 'send_message',
      arguments: '{"name":"张三","content":"你好","chatType":"direct"}'
    })
    expect(action?.name).toBe('send_message')
    expect(action?.arguments).toEqual({
      name: '张三',
      content: '你好',
      chatType: 'direct'
    })
  })

  it('rejects unknown tool names', () => {
    expect(parseAgentAction({ name: 'delete_all', arguments: {} })).toBeNull()
  })

  it('generates id when missing', () => {
    const action = parseAgentAction({ name: 'open_calendar', arguments: {} })
    expect(action?.id).toMatch(/^action-/)
  })

  it('covers all registered tool names', () => {
    expect(LINKMATE_AGENT_TOOL_NAMES.length).toBe(7)
    for (const name of LINKMATE_AGENT_TOOL_NAMES) {
      expect(parseAgentAction({ name, arguments: {} })).not.toBeNull()
    }
  })
})
