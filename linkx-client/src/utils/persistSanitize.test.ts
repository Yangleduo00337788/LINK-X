import { describe, expect, it } from 'vitest'
import { isEphemeralMediaUrl, stripEphemeralMediaUrl } from './mediaUrl'
import {
  sanitizeAppPersistState,
  sanitizeContactsPersistState,
  sanitizeMessagesForPersist
} from './persistSanitize'
import type { ChatMessage } from '../types'

describe('isEphemeralMediaUrl', () => {
  it('detects MinIO/S3 presigned query', () => {
    expect(
      isEphemeralMediaUrl(
        'http://127.0.0.1:9000/linkx/a.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=86400'
      )
    ).toBe(true)
  })

  it('detects local MinIO host without signature', () => {
    expect(isEphemeralMediaUrl('http://localhost:9000/linkx/2026/07/a.png')).toBe(true)
    expect(isEphemeralMediaUrl('http://127.0.0.1:9000/linkx/a.png')).toBe(true)
  })

  it('keeps external and data urls', () => {
    expect(isEphemeralMediaUrl('https://api.dicebear.com/7.x/adventurer/svg?seed=a')).toBe(false)
    expect(isEphemeralMediaUrl('data:image/png;base64,abc')).toBe(false)
    expect(stripEphemeralMediaUrl('https://cdn.example.com/a.png')).toBe(
      'https://cdn.example.com/a.png'
    )
    expect(stripEphemeralMediaUrl('http://127.0.0.1:9000/linkx/a.png?X-Amz-Signature=1')).toBe('')
  })
})

describe('sanitizeMessagesForPersist', () => {
  it('strips presigned file/avatar urls and image content', () => {
    const signed =
      'http://127.0.0.1:9000/linkx/x.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc'
    const input: Record<string, ChatMessage[]> = {
      s1: [
        {
          id: '1',
          sessionId: 's1',
          content: signed,
          time: '12:00',
          isSelf: false,
          type: 'image',
          isImage: true,
          fileUrl: signed,
          senderAvatar: signed
        }
      ]
    }
    const out = sanitizeMessagesForPersist(input)
    expect(out.s1[0].content).toBe('[图片]')
    expect(out.s1[0].fileUrl).toBeUndefined()
    expect(out.s1[0].senderAvatar).toBeUndefined()
  })
})

describe('sanitizeAppPersistState', () => {
  it('strips session and profile avatars that are ephemeral', () => {
    const signed = 'http://127.0.0.1:9000/linkx/a.png?X-Amz-Signature=1'
    const out = sanitizeAppPersistState({
      sessions: [
        {
          id: '1',
          name: '友',
          lastMessage: '',
          time: '',
          avatarText: '友',
          avatarColor: '#000',
          avatarUrl: signed,
          memberAvatars: [{ text: 'A', color: '#111', imageUrl: signed }]
        }
      ],
      userProfile: { avatar: signed, nickname: '我' },
      savedLogin: { avatar: signed, username: 'u' }
    })
    const sessions = out.sessions as Array<{ avatarUrl?: string; memberAvatars?: Array<{ imageUrl?: string }> }>
    expect(sessions[0].avatarUrl).toBeUndefined()
    expect(sessions[0].memberAvatars?.[0].imageUrl).toBeUndefined()
    expect((out.userProfile as { avatar: string }).avatar).toBe('')
    expect((out.savedLogin as { avatar: string }).avatar).toBe('')
  })
})

describe('sanitizeContactsPersistState', () => {
  it('strips friend avatarUrl when ephemeral', () => {
    const out = sanitizeContactsPersistState({
      items: [
        {
          id: '1',
          name: '小白',
          avatarUrl: 'http://localhost:9000/linkx/a.png?X-Amz-Signature=1'
        }
      ]
    })
    expect(out.items?.[0].avatarUrl).toBeUndefined()
  })
})
