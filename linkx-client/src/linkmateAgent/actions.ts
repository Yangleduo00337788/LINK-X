/**
 * 作者：yangleduo
 */
import type { LinkMateActionDefinition, LinkMateAgentToolName } from './types'

export const LINKMATE_AGENT_ACTIONS: Record<LinkMateAgentToolName, LinkMateActionDefinition> = {
  navigate: {
    name: 'navigate',
    risk: 'low',
    requireConfirm: false,
    labelKey: 'linkmateAgent.actionNavigate'
  },
  open_chat: {
    name: 'open_chat',
    risk: 'low',
    requireConfirm: false,
    labelKey: 'linkmateAgent.actionOpenChat'
  },
  open_search: {
    name: 'open_search',
    risk: 'low',
    requireConfirm: false,
    labelKey: 'linkmateAgent.actionOpenSearch'
  },
  open_calendar: {
    name: 'open_calendar',
    risk: 'low',
    requireConfirm: false,
    labelKey: 'linkmateAgent.actionOpenCalendar'
  }
}

export function getActionDefinition(name: LinkMateAgentToolName): LinkMateActionDefinition {
  return LINKMATE_AGENT_ACTIONS[name]
}
