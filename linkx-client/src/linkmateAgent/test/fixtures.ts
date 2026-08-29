/**
 * 作者：yangleduo
 */
import type { ChatSession } from '../../types'

const SESSION_DEFAULTS: Omit<ChatSession, 'id' | 'name'> = {
  lastMessage: '',
  time: '12:00',
  avatarText: '会',
  avatarColor: '#12b7f5',
  isReal: true
}

export function makeSession(
  overrides: Partial<ChatSession> & Pick<ChatSession, 'id' | 'name'>
): ChatSession {
  return {
    ...SESSION_DEFAULTS,
    avatarText: overrides.name.slice(0, 1) || SESSION_DEFAULTS.avatarText,
    ...overrides
  }
}

export const STANDARD_SESSIONS: ChatSession[] = [
  makeSession({ id: '101', name: '张三', peerUserId: 'u101' }),
  makeSession({
    id: '201',
    name: '项目群',
    isGroup: true,
    groupName: '项目群',
    avatarText: '项'
  }),
  makeSession({ id: '102', name: '李四', peerUserId: 'u102' })
]
