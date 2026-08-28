/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { getActionDefinition, LINKMATE_AGENT_ACTIONS } from './actions'
import { LINKMATE_AGENT_TOOL_NAMES } from './types'

describe('linkmateAgent actions', () => {
  it('defines all tools with risk and confirm rules', () => {
    for (const name of LINKMATE_AGENT_TOOL_NAMES) {
      const def = LINKMATE_AGENT_ACTIONS[name]
      expect(def.name).toBe(name)
      expect(def.labelKey.startsWith('linkmateAgent.')).toBe(true)
    }
  })

  it('requires confirm for write operations only', () => {
    expect(getActionDefinition('navigate').requireConfirm).toBe(false)
    expect(getActionDefinition('open_chat').requireConfirm).toBe(false)
    expect(getActionDefinition('send_message').requireConfirm).toBe(true)
    expect(getActionDefinition('create_calendar_event').requireConfirm).toBe(true)
    expect(getActionDefinition('add_favorite').requireConfirm).toBe(true)
  })

  it('assigns medium risk to write operations', () => {
    expect(getActionDefinition('send_message').risk).toBe('medium')
    expect(getActionDefinition('navigate').risk).toBe('low')
  })
})
