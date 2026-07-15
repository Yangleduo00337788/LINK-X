import { describe, it, expect } from 'vitest'
import { formatChatTime, formatFileSize, formatRelativeTime } from './chatTime'

describe('chatTime', () => {
  describe('formatChatTime', () => {
    it('should format timestamp to HH:mm', () => {
      const result = formatChatTime(Date.now())
      expect(result).toMatch(/^\d{2}:\d{2}$/)
    })

    it('should handle string timestamps', () => {
      const timestamp = Date.now()
      const result = formatChatTime(String(timestamp))
      expect(result).toMatch(/^\d{2}:\d{2}$/)
    })

    it('should handle null/undefined', () => {
      expect(formatChatTime(null)).toBe('')
      expect(formatChatTime(undefined)).toBe('')
    })
  })

  describe('formatFileSize', () => {
    it('should format bytes correctly', () => {
      expect(formatFileSize(500)).toBe('500 B')
      expect(formatFileSize(1024)).toBe('1.00 KB')
      expect(formatFileSize(1048576)).toBe('1.00 MB')
      expect(formatFileSize(1073741824)).toBe('1.00 GB')
    })

    it('should handle string input', () => {
      expect(formatFileSize('1024')).toBe('1.00 KB')
    })

    it('should handle null/undefined', () => {
      expect(formatFileSize(null)).toBe('')
      expect(formatFileSize(undefined)).toBe('')
    })

    it('should handle zero', () => {
      expect(formatFileSize(0)).toBe('0 B')
    })
  })

  describe('formatRelativeTime', () => {
    it('should format recent times as relative', () => {
      const now = Date.now()
      expect(formatRelativeTime(now)).toBe('刚刚')
      expect(formatRelativeTime(now - 60000)).toBe('1 分钟前')
      expect(formatRelativeTime(now - 3600000)).toBe('1 小时前')
    })

    it('should format older times as date', () => {
      const oldDate = new Date()
      oldDate.setDate(oldDate.getDate() - 7)
      const result = formatRelativeTime(oldDate.getTime())
      expect(result).toMatch(/^\d{1,2}\/\d{1,2}$/)
    })
  })
})
