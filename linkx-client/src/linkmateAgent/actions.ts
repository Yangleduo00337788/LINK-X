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
  },
  send_message: {
    name: 'send_message',
    risk: 'medium',
    requireConfirm: true,
    labelKey: 'linkmateAgent.actionSendMessage'
  },
  create_calendar_event: {
    name: 'create_calendar_event',
    risk: 'medium',
    requireConfirm: true,
    labelKey: 'linkmateAgent.actionCreateEvent'
  },
  add_favorite: {
    name: 'add_favorite',
    risk: 'medium',
    requireConfirm: true,
    labelKey: 'linkmateAgent.actionAddFavorite'
  }
}

export function getActionDefinition(name: LinkMateAgentToolName): LinkMateActionDefinition {
  return LINKMATE_AGENT_ACTIONS[name]
}
