/**
 * 作者：yangleduo
 */
import type { NavKey } from '../types'

/** Agent 动作风险等级 */
export type LinkMateActionRisk = 'low' | 'medium' | 'high' | 'critical'

/** Agent 支持的工具名 */
export type LinkMateAgentToolName =
  | 'navigate'
  | 'open_linkmate'
  | 'open_chat'
  | 'open_search'
  | 'open_calendar'
  | 'open_contacts'
  | 'send_message'
  | 'add_friend'
  | 'handle_friend_request'
  | 'handle_group_invitation'
  | 'create_calendar_event'
  | 'update_calendar_event'
  | 'delete_calendar_event'
  | 'add_favorite'
  | 'update_favorite'
  | 'delete_favorite'
  | 'tag_favorite'
  | 'create_folder'
  | 'upload_file'
  | 'publish_moment'
  | 'publish_short_video'
  | 'send_red_packet'
  | 'start_call'
  | 'create_group'
  | 'add_group_members'
  | 'update_setting'
  | 'recharge_balance'

export const LINKMATE_AGENT_TOOL_NAMES: LinkMateAgentToolName[] = [
  'navigate',
  'open_linkmate',
  'open_chat',
  'open_search',
  'open_calendar',
  'open_contacts',
  'send_message',
  'add_friend',
  'handle_friend_request',
  'handle_group_invitation',
  'create_calendar_event',
  'update_calendar_event',
  'delete_calendar_event',
  'add_favorite',
  'update_favorite',
  'delete_favorite',
  'tag_favorite',
  'create_folder',
  'upload_file',
  'publish_moment',
  'publish_short_video',
  'send_red_packet',
  'start_call',
  'create_group',
  'add_group_members',
  'update_setting',
  'recharge_balance'
]

export interface LinkMateAgentAction {
  id: string
  name: LinkMateAgentToolName
  arguments: Record<string, unknown>
}

export interface LinkMateClientContext {
  currentNav?: NavKey
  currentSessionId?: string
  currentSessionTitle?: string
  /** 客户端本地当天 YYYY-MM-DD，供模型计算「明天」等相对日期 */
  todayDate?: string
  /** 近期会话摘要，供模型选择准确目标 */
  recentSessions?: string
}

export interface LinkMateActionDefinition {
  name: LinkMateAgentToolName
  risk: LinkMateActionRisk
  /** 是否需要用户二次确认 */
  requireConfirm: boolean
  labelKey: string
}

export interface LinkMateActionResult {
  ok: boolean
  message?: string
}

export type LinkMateAgentPhase = 'idle' | 'planning' | 'executing' | 'confirming'

export interface LinkMateAgentCursorState {
  visible: boolean
  x: number
  y: number
  clicking: boolean
}

export interface LinkMateAgentRunState {
  phase: LinkMateAgentPhase
  currentAction: LinkMateAgentAction | null
  /** SSE 流式 tool_call 预览（规划阶段） */
  plannedActions: LinkMateAgentAction[]
  queue: LinkMateAgentAction[]
  completed: Array<{ action: LinkMateAgentAction; result: LinkMateActionResult }>
  cancelled: boolean
  thinkingText: string
  cursor: LinkMateAgentCursorState
}

export const VALID_NAV_KEYS: NavKey[] = [
  'chat',
  'contacts',
  'favorites',
  'files',
  'calendar',
  'moments',
  'shortVideo',
  'balance',
  'linkmate',
  'settings'
]

export function isNavKey(value: unknown): value is NavKey {
  return typeof value === 'string' && (VALID_NAV_KEYS as string[]).includes(value)
}

export function parseAgentAction(raw: {
  id?: unknown
  name?: unknown
  arguments?: unknown
}): LinkMateAgentAction | null {
  const name = raw.name
  if (typeof name !== 'string') return null
  if (!LINKMATE_AGENT_TOOL_NAMES.includes(name as LinkMateAgentToolName)) return null

  let args: Record<string, unknown> = {}
  if (typeof raw.arguments === 'string') {
    try {
      const parsed = JSON.parse(raw.arguments) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        args = parsed as Record<string, unknown>
      }
    } catch {
      args = {}
    }
  } else if (raw.arguments && typeof raw.arguments === 'object' && !Array.isArray(raw.arguments)) {
    args = raw.arguments as Record<string, unknown>
  }

  return {
    id: typeof raw.id === 'string' && raw.id ? raw.id : `action-${Date.now()}`,
    name: name as LinkMateAgentToolName,
    arguments: args
  }
}
