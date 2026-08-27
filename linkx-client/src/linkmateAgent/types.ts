/**
 * 作者：yangleduo
 */
import type { NavKey } from '../types'

/** Agent 动作风险等级 */
export type LinkMateActionRisk = 'low' | 'medium' | 'high' | 'critical'

/** Phase 1 支持的工具名 */
export type LinkMateAgentToolName = 'navigate' | 'open_chat' | 'open_search' | 'open_calendar'

export interface LinkMateAgentAction {
  id: string
  name: LinkMateAgentToolName
  arguments: Record<string, unknown>
}

export interface LinkMateClientContext {
  currentNav?: NavKey
  currentSessionId?: string
  currentSessionTitle?: string
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

export interface LinkMateAgentRunState {
  phase: LinkMateAgentPhase
  currentAction: LinkMateAgentAction | null
  queue: LinkMateAgentAction[]
  completed: Array<{ action: LinkMateAgentAction; result: LinkMateActionResult }>
  cancelled: boolean
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
  if (!['navigate', 'open_chat', 'open_search', 'open_calendar'].includes(name)) return null

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
