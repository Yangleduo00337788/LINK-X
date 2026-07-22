import { describe, it, expect } from 'vitest'
import { conversationToSession, messageToChatMessage, messagePreviewFromItem } from './chatMapper'

describe('chatMapper', () => {
  describe('conversationToSession', () => {
    it('should convert private chat conversation', () => {
      const conv = {
        id: '123',
        type: 1,
        peerUserId: '456',
        peerNickname: 'Zhang',
        peerAvatar: 'https://example.com/avatar.png',
        lastMessage: 'hi',
        lastMessageTime: Date.now()
      }
      const session = conversationToSession(conv)
      expect(session.id).toBe('123')
      expect(session.name).toBe('Zhang')
      expect(session.isGroup).toBe(false)
      expect(session.peerUserId).toBe('456')
    })

    it('should convert group conversation', () => {
      const conv = {
        id: '789',
        type: 2,
        name: 'DevGroup',
        lastMessage: 'discuss',
        lastMessageTime: Date.now()
      }
      const session = conversationToSession(conv)
      expect(session.id).toBe('789')
      expect(session.name).toBe('DevGroup')
      expect(session.groupName).toBe('DevGroup')
      expect(session.isGroup).toBe(true)
    })

    it('should use myRemark over group name', () => {
      const conv = {
        id: '789',
        type: 2,
        name: 'DevGroup',
        myRemark: 'Team',
        lastMessage: 'hi'
      }
      const session = conversationToSession(conv)
      expect(session.name).toBe('Team')
      expect(session.groupName).toBe('DevGroup')
      expect(session.groupRemark).toBe('Team')
    })

    it('should map peerOnline to session.online', () => {
      const conv = {
        id: '123',
        type: 1,
        peerUserId: '456',
        peerNickname: 'Zhang',
        peerOnline: true
      }
      const session = conversationToSession(conv)
      expect(session.online).toBe(true)
    })

    it('should use peerRemark over peerNickname', () => {
      const conv = {
        id: '123',
        type: 1,
        peerUserId: '456',
        peerNickname: 'Zhang',
        peerRemark: 'Buddy',
        lastMessage: 'hi'
      }
      const session = conversationToSession(conv)
      expect(session.name).toBe('Buddy')
    })

    it('should fallback to default values', () => {
      const conv = {
        id: '123',
        type: 1
      }
      const session = conversationToSession(conv)
      expect(session.name).toBe('\u597d\u53cb')
      expect(session.avatarText).toBe('\u597d')
    })
  })

  describe('messageToChatMessage', () => {
    it('should convert text message', () => {
      const msg = {
        id: 'msg1',
        conversationId: 'conv1',
        senderId: 'user1',
        type: 'text',
        content: 'Hello',
        createTime: Date.now()
      }
      const result = messageToChatMessage(msg, 'conv1')
      expect(result.id).toBe('msg1')
      expect(result.content).toBe('Hello')
      expect(result.type).toBe('text')
    })

    it('should handle image message', () => {
      const msg = {
        id: 'msg2',
        conversationId: 'conv1',
        senderId: 'user1',
        type: 'image',
        fileUrl: 'https://example.com/image.png',
        createTime: Date.now()
      }
      const result = messageToChatMessage(msg, 'conv1')
      expect(result.content).toBe('https://example.com/image.png')
      expect(result.isImage).toBe(true)
    })

    it('should handle file message', () => {
      const msg = {
        id: 'msg3',
        conversationId: 'conv1',
        senderId: 'user1',
        type: 'file',
        fileName: 'document.pdf',
        fileUrl: 'https://example.com/doc.pdf',
        createTime: Date.now()
      }
      const result = messageToChatMessage(msg, 'conv1')
      expect(result.content).toBe('document.pdf')
      expect(result.fileName).toBe('document.pdf')
    })

    it('should map voice message fields', () => {
      const msg = {
        id: 'v1',
        conversationId: 'conv1',
        senderId: 'user1',
        type: 'voice',
        content: 'ignored',
        fileUrl: 'https://cdn.example.com/a.webm',
        voiceDuration: 8,
        createTime: Date.now()
      }
      const result = messageToChatMessage(msg as any, 'conv1')
      expect(result.type).toBe('voice')
      expect(result.content).toBe('[\u8bed\u97f3]')
      expect(result.voiceDuration).toBe(8)
      expect(result.voiceUrl).toContain('cdn.example.com')
    })
  })

  describe('messagePreviewFromItem', () => {
    it('should return file preview', () => {
      const msg = { type: 'file' as const, fileName: 'doc.pdf', content: '' }
      expect(messagePreviewFromItem(msg)).toBe('[\u6587\u4ef6] doc.pdf')
    })

    it('should return image preview', () => {
      const msg = { type: 'image' as const, content: '' }
      expect(messagePreviewFromItem(msg)).toBe('[\u56fe\u7247]')
    })

    it('should return text content', () => {
      const msg = { type: 'text' as const, content: 'Hello World' }
      expect(messagePreviewFromItem(msg)).toBe('Hello World')
    })

    it('should preview voice as voice label', () => {
      expect(messagePreviewFromItem({ type: 'voice', content: 'x' } as any)).toBe('[\u8bed\u97f3]')
    })
  })
})
