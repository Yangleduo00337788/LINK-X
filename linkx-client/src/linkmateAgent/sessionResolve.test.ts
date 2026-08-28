/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { resolveChatSession, resolveChatSessionId } from './sessionResolve'
import { makeContact, makeSession, STANDARD_SESSIONS } from './test/fixtures'
import { setupAgentTestStores } from './test/storeHarness'
import { SYSTEM_NOTIFY_SESSION_ID } from '../types'

describe('sessionResolve', () => {
  it('resolves by conversationId', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const session = resolveChatSession({ conversationId: '201' })
    expect(session?.id).toBe('201')
  })

  it('prefers direct chat when chatType is direct', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const session = resolveChatSession({ name: '张三', chatType: 'direct' })
    expect(session?.id).toBe('101')
    expect(session?.isGroup).toBe(false)
  })

  it('prefers group when chatType is group', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const session = resolveChatSession({ name: '项目群', chatType: 'group' })
    expect(session?.id).toBe('201')
    expect(session?.isGroup).toBe(true)
  })

  it('does not pick group containing friend name when direct preferred', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const session = resolveChatSession({ name: '张三', chatType: 'direct' })
    expect(session?.id).toBe('101')
    expect(session?.id).not.toBe('202')
  })

  it('falls back to current session when name missing', () => {
    const current = STANDARD_SESSIONS[0]
    setupAgentTestStores({ sessions: STANDARD_SESSIONS, currentSession: current })
    const session = resolveChatSession({})
    expect(session?.id).toBe('101')
  })

  it('resolves friend via contacts when session list has peer match', () => {
    setupAgentTestStores({
      sessions: [makeSession({ id: '301', name: '王五', peerUserId: 'u_wang', isGroup: false })],
      contacts: [makeContact({ id: 'u_wang', userId: 9001, name: '王五备注', remark: '王五备注' })]
    })
    const session = resolveChatSession({ name: '王五备注', chatType: 'direct' })
    expect(session?.id).toBe('301')
  })

  it('excludes virtual notify sessions from name search', () => {
    setupAgentTestStores({
      sessions: [
        makeSession({
          id: SYSTEM_NOTIFY_SESSION_ID,
          name: '日程提醒',
          isSystemNotify: true,
          isReal: false
        }),
        makeSession({ id: '101', name: '张三', peerUserId: 'u_zhang', isGroup: false })
      ]
    })
    const session = resolveChatSession({ name: '张三', chatType: 'direct' })
    expect(session?.id).toBe('101')
  })

  it('returns null when no match', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    expect(resolveChatSession({ name: '不存在的人' })).toBeNull()
    expect(resolveChatSessionId({ name: '不存在的人' })).toBe('')
  })

  it('does not false-match direct chats on unrelated short queries', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    expect(resolveChatSession({ name: '不存在的人', chatType: 'direct' })).toBeNull()
  })

  it('matches partial group name', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const session = resolveChatSession({ name: '技术', chatType: 'group' })
    expect(session?.id).toBe('203')
  })
})
