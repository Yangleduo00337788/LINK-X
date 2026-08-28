/**
 * 作者：yangleduo
 */
import type { ChatSession, ContactItem } from '../../types'

export function makeSession(
  overrides: Partial<ChatSession> & Pick<ChatSession, 'id' | 'name'>
): ChatSession {
  return {
    lastMessage: '',
    time: '12:00',
    avatarText: overrides.name.slice(0, 1),
    avatarColor: '#12b7f5',
    isReal: true,
    ...overrides
  }
}

export function makeContact(
  overrides: Partial<ContactItem> & Pick<ContactItem, 'id' | 'name'>
): ContactItem {
  return {
    avatarText: overrides.name.slice(0, 1),
    avatarColor: '#12b7f5',
    group: '我的好友',
    ...overrides
  }
}

/** 标准测试会话集：好友、群聊、同名歧义场景 */
export const STANDARD_SESSIONS: ChatSession[] = [
  makeSession({ id: '101', name: '张三', peerUserId: 'u_zhang', isGroup: false }),
  makeSession({ id: '102', name: '李四', peerUserId: 'u_li', isGroup: false }),
  makeSession({ id: '201', name: '项目群', isGroup: true, groupName: '项目群' }),
  makeSession({ id: '202', name: '张三、李四', isGroup: true, groupName: '张三、李四' }),
  makeSession({ id: '203', name: '技术交流群', isGroup: true, groupName: '技术交流群' })
]
