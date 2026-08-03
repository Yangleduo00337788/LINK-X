import { describe, expect, it, vi } from 'vitest'

vi.mock('@/i18n', () => ({
  tGlobal: (key: string) => {
    const map: Record<string, string> = {
      'common.none': 'N/A',
      'common.normal': '正常',
      'common.frozen': '冻结',
    }
    return map[key] ?? key
  },
}))

import { displayCount, displayOrNone, formatIp, formatTime, userStatusLabel, userStatusType } from './format'

describe('format', () => {
  describe('formatTime', () => {
    it('formats valid ISO timestamps', () => {
      expect(formatTime('2024-06-01T08:30:00')).toBe('2024-06-01 08:30:00')
    })

    it('returns "-" for empty or invalid values', () => {
      expect(formatTime(null)).toBe('-')
      expect(formatTime(undefined)).toBe('-')
      expect(formatTime('')).toBe('-')
      expect(formatTime('not-a-date')).toBe('-')
    })
  })

  describe('displayOrNone', () => {
    it('returns localized N/A for empty values', () => {
      expect(displayOrNone(null)).toBe('N/A')
      expect(displayOrNone('')).toBe('N/A')
      expect(displayOrNone('   ')).toBe('N/A')
    })

    it('returns the original value when present', () => {
      expect(displayOrNone('hello')).toBe('hello')
    })
  })

  describe('displayCount', () => {
    it('returns localized N/A for empty numeric values', () => {
      expect(displayCount(null)).toBe('N/A')
      expect(displayCount(undefined)).toBe('N/A')
    })

    it('returns stringified number when present', () => {
      expect(displayCount(0)).toBe('0')
      expect(displayCount(12)).toBe('12')
    })
  })

  describe('formatIp', () => {
    it('returns localized N/A for empty values', () => {
      expect(formatIp(null)).toBe('N/A')
      expect(formatIp('')).toBe('N/A')
    })

    it('maps loopback IPv6 to 127.0.0.1', () => {
      expect(formatIp('::1')).toBe('127.0.0.1')
      expect(formatIp('0:0:0:0:0:0:0:1')).toBe('127.0.0.1')
    })

    it('unwraps bracketed IPv6', () => {
      expect(formatIp('[::1]')).toBe('127.0.0.1')
    })

    it('strips port from IPv4-mapped host:port', () => {
      expect(formatIp('192.168.1.1:8080')).toBe('192.168.1.1')
    })

    it('maps ::ffff hex IPv4', () => {
      expect(formatIp('::ffff:c0a8:0001')).toBe('192.168.0.1')
    })

    it('returns raw value for other IPs', () => {
      expect(formatIp('10.0.0.5')).toBe('10.0.0.5')
    })
  })

  describe('userStatusLabel', () => {
    it('maps known statuses via i18n', () => {
      expect(userStatusLabel(1)).toBe('正常')
      expect(userStatusLabel(0)).toBe('冻结')
    })

    it('falls back for unknown or null status', () => {
      expect(userStatusLabel(null)).toBe('-')
      expect(userStatusLabel(undefined)).toBe('-')
      expect(userStatusLabel(2)).toBe('2')
    })
  })

  describe('userStatusType', () => {
    it('maps status to naive-ui tag types', () => {
      expect(userStatusType(1)).toBe('success')
      expect(userStatusType(0)).toBe('error')
      expect(userStatusType(undefined)).toBe('default')
      expect(userStatusType(99)).toBe('default')
    })
  })
})
