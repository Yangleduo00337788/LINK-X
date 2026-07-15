import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface RedPacket {
  id: string
  senderId: string
  senderNickname?: string
  senderAvatar?: string
  conversationId: string
  type: 'normal' | 'lucky'
  totalAmount: number
  totalCount: number
  remainingAmount: number
  remainingCount: number
  greeting: string
  status: 'active' | 'expired' | 'finished'
  time: string
  received: boolean
  receivedAmount?: number
  records?: RedPacketRecord[]
}

export interface RedPacketRecord {
  id: string
  userId: string
  nickname?: string
  avatar?: string
  amount: number
  isLucky: boolean
  time: string
}

export interface SendRedPacketPayload {
  conversationId: string
  type: 'normal' | 'lucky'
  totalAmount: number
  totalCount: number
  greeting?: string
}

/**
 * 发送红包
 */
export function sendRedPacket(payload: SendRedPacketPayload) {
  return apiClient.post<never, ApiResult<RedPacket>>('/red-packet', payload)
}

/**
 * 领取红包
 */
export function receiveRedPacket(redPacketId: string) {
  return apiClient.post<never, ApiResult<RedPacket>>(`/red-packet/${redPacketId}/receive`)
}

/**
 * 获取红包详情
 */
export function getRedPacketDetail(redPacketId: string) {
  return apiClient.get<never, ApiResult<RedPacket>>(`/red-packet/${redPacketId}`)
}

/**
 * 获取会话中的红包列表
 */
export function listRedPackets(conversationId: string) {
  return apiClient.get<never, ApiResult<RedPacket[]>>(`/red-packet/conversation/${conversationId}`)
}
