/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Mock types
interface MockChatMessage {
  id: string
  sessionId: string
  content: string
  time: string
  isSelf: boolean
  type: string
  fileName?: string
  fileSize?: string
  fileUrl?: string
  isImage?: boolean
  voiceDuration?: number
  voiceUrl?: string
  redPacketGreeting?: string
  redPacketAmount?: string
  redPacketOpened?: boolean
}

describe('ChatPanel - 聊天面板逻辑测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('消息类型处理', () => {
    it('应该正确生成消息预览', () => {
      const messagePreview = (msg: MockChatMessage): string => {
        if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
        if (msg.type === 'image' || msg.isImage) return '[图片]'
        if (msg.type === 'voice') return '[语音]'
        if (msg.type === 'redPacket') return `[红包] ${msg.redPacketGreeting || '恭喜发财'}`
        return msg.content
      }

      expect(messagePreview({ id: '1', sessionId: 's1', content: 'Hello', time: '12:00', isSelf: true, type: 'text' })).toBe('Hello')
      expect(messagePreview({ id: '2', sessionId: 's1', content: 'photo.jpg', time: '12:01', isSelf: true, type: 'file', fileName: 'photo.jpg' })).toBe('[文件] photo.jpg')
      expect(messagePreview({ id: '3', sessionId: 's1', content: '', time: '12:02', isSelf: false, type: 'image', isImage: true })).toBe('[图片]')
      expect(messagePreview({ id: '4', sessionId: 's1', content: '', time: '12:03', isSelf: true, type: 'voice' })).toBe('[语音]')
      expect(messagePreview({ id: '5', sessionId: 's1', content: '', time: '12:04', isSelf: true, type: 'redPacket', redPacketGreeting: '新年快乐' })).toBe('[红包] 新年快乐')
    })

    it('应该正确格式化时间', () => {
      const nowTime = (): string => {
        const now = new Date()
        return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
      }

      const time = nowTime()
      expect(time).toMatch(/^\d{2}:\d{2}$/)
    })
  })

  describe('消息验证', () => {
    it('文本消息不能为空', () => {
      const validateTextMessage = (content: string, type: string): boolean => {
        const trimmed = content.trim()
        if (type === 'text' && !trimmed) return false
        return true
      }

      expect(validateTextMessage('', 'text')).toBe(false)
      expect(validateTextMessage('Hello', 'text')).toBe(true)
      expect(validateTextMessage('   ', 'text')).toBe(false)
    })

    it('文件消息需要有文件', () => {
      const validateFileMessage = (rawFile: File | null, fileUrl: string | undefined, type: string): boolean => {
        if (type === 'file') {
          if (!rawFile && !fileUrl) return false
        }
        return true
      }

      expect(validateFileMessage(null, undefined, 'file')).toBe(false)
      expect(validateFileMessage(new File([], 'test.pdf'), undefined, 'file')).toBe(true)
      expect(validateFileMessage(null, 'https://example.com/file.pdf', 'file')).toBe(true)
    })

    it('语音消息需要时长', () => {
      const validateVoiceMessage = (voiceDuration: number | undefined): boolean => {
        if (!voiceDuration) return false
        return true
      }

      expect(validateVoiceMessage(undefined)).toBe(false)
      expect(validateVoiceMessage(0)).toBe(false)
      expect(validateVoiceMessage(10)).toBe(true)
    })

    it('红包消息需要金额', () => {
      const validateRedPacketMessage = (redPacketAmount: string | undefined): boolean => {
        if (!redPacketAmount) return false
        return true
      }

      expect(validateRedPacketMessage(undefined)).toBe(false)
      expect(validateRedPacketMessage('')).toBe(false)
      expect(validateRedPacketMessage('100')).toBe(true)
    })
  })

  describe('会话排序', () => {
    it('置顶会话应该排在前面', () => {
      interface Session {
        id: string
        pinned: boolean
      }

      const sortSessions = (sessions: Session[]): Session[] => {
        return [...sessions].sort((a, b) => {
          if (a.pinned && !b.pinned) return -1
          if (!a.pinned && b.pinned) return 1
          return 0
        })
      }

      const sessions: Session[] = [
        { id: '1', pinned: false },
        { id: '2', pinned: true },
        { id: '3', pinned: false },
        { id: '4', pinned: true }
      ]

      const sorted = sortSessions(sessions)
      expect(sorted[0].id).toBe('2')
      expect(sorted[1].id).toBe('4')
      expect(sorted[2].id).toBe('1')
      expect(sorted[3].id).toBe('3')
    })
  })

  describe('消息ID去重', () => {
    it('应该正确处理重复消息', () => {
      const filterDuplicates = <T extends { id: string }>(messages: T[]): T[] => {
        const seen = new Set<string>()
        return messages.filter(m => {
          if (seen.has(m.id)) return false
          seen.add(m.id)
          return true
        })
      }

      const messages = [
        { id: '1', content: 'Hello' },
        { id: '2', content: 'World' },
        { id: '1', content: 'Duplicate' },
        { id: '3', content: 'Test' }
      ]

      const filtered = filterDuplicates(messages)
      expect(filtered.length).toBe(3)
      expect(filtered.map(m => m.id)).toEqual(['1', '2', '3'])
    })
  })

  describe('用户ID验证', () => {
    it('应该正确验证用户ID格式', () => {
      const sanitizeUserId = (raw: unknown): string => {
        if (raw == null) return ''
        const s = String(raw).trim()
        // 只接受纯数字字符串
        return /^\d{1,32}$/.test(s) ? s : ''
      }

      expect(sanitizeUserId(null)).toBe('')
      expect(sanitizeUserId(undefined)).toBe('')
      expect(sanitizeUserId('')).toBe('')
      expect(sanitizeUserId('   ')).toBe('')
      expect(sanitizeUserId('abc')).toBe('')
      expect(sanitizeUserId('123abc')).toBe('')
      expect(sanitizeUserId('123')).toBe('123')
      // 注意：JavaScript 数字精度问题，字符串形式更可靠
      expect(sanitizeUserId('123456789012345678')).toBe('123456789012345678')
      expect(sanitizeUserId('12345678901234567890123456789012345678'.repeat(2))).toBe('')
    })

    it('应该拒绝超长数字字符串', () => {
      const sanitizeUserId = (raw: unknown): string => {
        if (raw == null) return ''
        const s = String(raw).trim()
        return /^\d{1,32}$/.test(s) ? s : ''
      }

      // 超过32位数字应该被拒绝
      const longNumber = '123456789012345678901234567890123' // 33位
      expect(sanitizeUserId(longNumber)).toBe('')
    })
  })
})
