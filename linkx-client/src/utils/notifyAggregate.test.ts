import { describe, expect, it } from 'vitest'
import { aggregateNotifications, isInQuietHours } from './notifyAggregate'

describe('aggregateNotifications', () => {
  it('merges same type + relatedId within 24h', () => {
    const now = Date.now()
    const list = aggregateNotifications([
      {
        id: '1',
        senderId: 'a',
        senderName: 'Alice',
        type: 'moments_like',
        relatedId: 'post1',
        content: '赞了',
        readStatus: 0,
        createTime: new Date(now).toISOString()
      },
      {
        id: '2',
        senderId: 'b',
        senderName: 'Bob',
        type: 'moments_like',
        relatedId: 'post1',
        content: '赞了',
        readStatus: 1,
        createTime: new Date(now - 1000).toISOString()
      },
      {
        id: '3',
        senderId: 'c',
        senderName: 'Cara',
        type: 'moments_comment',
        relatedId: 'post1',
        content: '评了',
        readStatus: 0,
        createTime: new Date(now).toISOString()
      }
    ])
    expect(list).toHaveLength(2)
    const likes = list.find(n => n.type === 'moments_like')!
    expect(likes.aggregateCount).toBe(2)
    expect(likes.aggregateNames).toContain('Bob')
    expect(likes.readStatus).toBe(0)
  })

  it('does not merge without relatedId', () => {
    const list = aggregateNotifications([
      {
        id: '1',
        senderId: 'a',
        senderName: 'A',
        type: 'system_x',
        content: 'x',
        readStatus: 0,
        createTime: new Date().toISOString()
      },
      {
        id: '2',
        senderId: 'b',
        senderName: 'B',
        type: 'system_x',
        content: 'y',
        readStatus: 0,
        createTime: new Date().toISOString()
      }
    ])
    expect(list).toHaveLength(2)
  })
})

describe('isInQuietHours', () => {
  it('handles same-day window', () => {
    const d = new Date()
    d.setHours(10, 0, 0, 0)
    expect(isInQuietHours(d, true, '09:00', '11:00')).toBe(true)
    expect(isInQuietHours(d, true, '11:00', '12:00')).toBe(false)
  })

  it('handles overnight window', () => {
    const night = new Date()
    night.setHours(23, 0, 0, 0)
    expect(isInQuietHours(night, true, '22:00', '08:00')).toBe(true)
    const noon = new Date()
    noon.setHours(12, 0, 0, 0)
    expect(isInQuietHours(noon, true, '22:00', '08:00')).toBe(false)
  })

  it('respects disabled flag', () => {
    const d = new Date()
    d.setHours(23, 0, 0, 0)
    expect(isInQuietHours(d, false, '22:00', '08:00')).toBe(false)
  })
})
