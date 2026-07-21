import { describe, it, expect } from 'vitest'
import { conversationToSession, messageToChatMessage, messagePreviewFromItem } from './chatMapper'

describe('chatMapper', () => {
  describe('conversationToSession', () => {
    it('should convert private chat conversation', () => {
      const conv = {
        id: '123',
        type: 1,
        peerUserId: '456',
        peerNickname: '张三',
        peerAvatar: 'https://example.com/avatar.png',
        lastMessage: '你好',
        lastMessageTime: Date.now()
      }
      const session = conversationToSession(conv)
      expect(session.id).toBe('123')
      expect(session.name).toBe('张三')
      expect(session.isGroup).toBe(false)
      expect(session.peerUserId).toBe('456')
    })

    it('should convert group conversation', () => {
      const conv = {
        id: '789',
        type: 2,
        name: '开发群',
        lastMessage: '今天讨论新功能',
        lastMessageTime: Date.now()
      }
      const session = conversationToSession(conv)
      expect(session.id).toBe('789')
      expect(session.name).toBe('开发群')
      expect(session.groupName).toBe('开发群')
      expect(session.isGroup).toBe(true)
    })

    it('should use myRemark over group name', () => {
      const conv = {
        id: '789',
        type: 2,
        name: '开发群',
        myRemark: '项目组',
        lastMessage: 'hi'
      }
      const session = conversationToSession(conv)
      expect(session.name).toBe('项目组')
      expect(session.groupName).toBe('开发群')
      expect(session.groupRemark).toBe('项目组')
    })

    it('should map peerOnline to session.online', () => {
      const conv = {
        id: '123',
        type: 1,
        peerUserId: '456',
        peerNickname: '张三',
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
        peerNickname: '张三',
        peerRemark: '好友小张',
        lastMessage: '嗨'
      }
      const session = conversationToSession(conv)
      expect(session.name).toBe('好友小张')
    })

    it('should fallback to default values', () => {
      const conv = {
        id: '123',
        type: 1
      }
      const session = conversationToSession(conv)
      expect(session.name).toBe('好友')
      expect(session.avatarText).toBe('好') // name.charAt(0) = '好'
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
  })

  describe('messagePreviewFromItem', () => {
    it('should return file preview', () => {
      const msg = { type: 'file' as const, fileName: 'doc.pdf', content: '' }
      expect(messagePreviewFromItem(msg)).toBe('[文件] doc.pdf')
    })

    it('should return image preview', () => {
      const msg = { type: 'image' as const, content: '' }
      expect(messagePreviewFromItem(msg)).toBe('[图片]')
    })

    it('should return text content', () => {
      const msg = { type: 'text' as const, content: 'Hello World' }
      expect(messagePreviewFromItem(msg)).toBe('Hello World')
    })
  })
})
