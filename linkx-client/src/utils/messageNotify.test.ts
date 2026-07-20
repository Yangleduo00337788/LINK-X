import { describe, expect, it } from 'vitest'
import {
  contentMentionsUser,
  shouldAlertForSession
} from './messageNotify'
import type { ChatSession } from '../types'
import type { MessageItem } from '../types/chat'

function msg(content: string, extra: Partial<MessageItem> = {}): MessageItem {
  return {
    id: '1',
    conversationId: 'c1',
    senderId: 'u2',
    type: 'text',
    content,
    ...extra
  }
}

function session(partial: Partial<ChatSession>): ChatSession {
  return {
    id: 'c1',
    name: '群聊',
    lastMessage: '',
    time: '',
    avatarText: '群',
    avatarColor: '#12b7f5',
    ...partial
  }
}

describe('contentMentionsUser', () => {
  it('matches @nickname', () => {
    expect(contentMentionsUser('你好 @小明 在吗', ['小明', 'xiaoming'])).toBe(true)
  })

  it('matches @所有人', () => {
    expect(contentMentionsUser('@所有人 开会', ['小明'])).toBe(true)
  })

  it('returns false when not mentioned', () => {
    expect(contentMentionsUser('大家好', ['小明'])).toBe(false)
  })
})

describe('shouldAlertForSession', () => {
  const opts = { notifyAtMe: true, myNickname: '小明', myUsername: 'xiaoming' }

  it('alerts for unmuted sessions', () => {
    expect(shouldAlertForSession(session({ muted: false }), msg('hi'), opts)).toBe(true)
  })

  it('skips muted private chats', () => {
    expect(
      shouldAlertForSession(session({ muted: true, isGroup: false }), msg('hi'), opts)
    ).toBe(false)
  })

  it('alerts muted group when @me and notifyAtMe on', () => {
    expect(
      shouldAlertForSession(
        session({ muted: true, isGroup: true }),
        msg('@小明 看下'),
        opts
      )
    ).toBe(true)
  })

  it('skips muted group when not @me', () => {
    expect(
      shouldAlertForSession(session({ muted: true, isGroup: true }), msg('普通消息'), opts)
    ).toBe(false)
  })

  it('skips muted @me when notifyAtMe off', () => {
    expect(
      shouldAlertForSession(session({ muted: true, isGroup: true }), msg('@小明'), {
        ...opts,
        notifyAtMe: false
      })
    ).toBe(false)
  })
})
