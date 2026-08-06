import { describe, it, expect } from 'vitest'
import {
  compareSnowflakeId,
  isMessageIdAtOrBefore,
  formatLastSeen,
  groupReadCountLabel,
  privateStatusLabel,
  fileStatusFromSendStatus
} from './messageStatus'
import type { ChatMessage } from '../types'

const t = (key: string, params?: Record<string, unknown>) => {
  if (key === 'chat.readCount') return `${params?.read}/${params?.total}人已读`
  if (key === 'chat.statusRead') return '已读'
  if (key === 'chat.statusDelivered') return '已送达'
  if (key === 'chat.statusSent') return '已发送'
  if (key === 'chat.lastSeenJustNow') return '刚刚在线'
  if (key === 'chat.lastSeenMinutes') return `${params?.n}分钟前在线`
  return key
}

describe('messageStatus', () => {
  it('compareSnowflakeId 应比较同长度数字 ID', () => {
    expect(compareSnowflakeId('100', '200')).toBe(-1)
    expect(compareSnowflakeId('200', '100')).toBe(1)
    expect(compareSnowflakeId('100', '100')).toBe(0)
  })

  it('isMessageIdAtOrBefore 判断游标范围', () => {
    expect(isMessageIdAtOrBefore('100', '200')).toBe(true)
    expect(isMessageIdAtOrBefore('300', '200')).toBe(false)
    expect(isMessageIdAtOrBefore('200', '200')).toBe(true)
  })

  it('formatLastSeen 应格式化相对时间', () => {
    const now = Date.now()
    expect(formatLastSeen(now - 30_000, t)).toBe('刚刚在线')
    expect(formatLastSeen(now - 120_000, t)).toBe('2分钟前在线')
  })

  it('groupReadCountLabel 群聊已读人数', () => {
    const msg = {
      isSelf: true,
      readCount: 3,
      totalMembers: 5
    } as ChatMessage
    expect(groupReadCountLabel(msg, t)).toBe('3/5人已读')
  })

  it('privateStatusLabel 单聊状态文案', () => {
    expect(privateStatusLabel({ isSelf: true, sendStatus: 'read' } as ChatMessage, t)).toBe('已读')
    expect(privateStatusLabel({ isSelf: true, sendStatus: 'delivered' } as ChatMessage, t)).toBe('已送达')
  })

  it('fileStatusFromSendStatus 与 sendStatus 同步', () => {
    const msg = { isSelf: true, sendStatus: 'read' } as ChatMessage
    expect(fileStatusFromSendStatus(msg, t)).toBe('已读')
  })
})
