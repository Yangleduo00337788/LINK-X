import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface CallInviteResult {
  callId: string
  conversationId: string
  callType: 'voice' | 'video'
  status: string
  peerUserId?: string
  peerNickname?: string
  peerAvatar?: string
}

export interface CallInvitePayload {
  conversationId: string
  callType: 'voice' | 'video'
}

export interface CallSignalPayload {
  callId: string
  signalType: 'offer' | 'answer' | 'ice-candidate'
  sdp?: string
  candidate?: string
}

export interface CallEventPayload {
  callId: string
  conversationId: string | number
  callType: 'voice' | 'video'
  status?: string
  fromUserId?: string | number
  toUserId?: string | number
  fromNickname?: string
  fromAvatar?: string
  signalType?: 'offer' | 'answer' | 'ice-candidate'
  sdp?: string
  candidate?: string
}

export function inviteCall(payload: CallInvitePayload) {
  // 雪花 ID 超过 Number.MAX_SAFE_INTEGER，禁止 Number() 转换，否则会精度丢失
  // 例：437044203068833792 → 437044203068833800，导致「无权访问该会话」
  return apiClient.post<never, ApiResult<CallInviteResult>>('/call/invite', {
    conversationId: String(payload.conversationId),
    callType: payload.callType
  })
}

export function cancelCall(callId: string) {
  return apiClient.post<never, ApiResult<null>>('/call/cancel', { callId })
}

export function acceptCall(callId: string) {
  return apiClient.post<never, ApiResult<null>>('/call/accept', { callId })
}

export function rejectCall(callId: string) {
  return apiClient.post<never, ApiResult<null>>('/call/reject', { callId })
}

export function hangupCall(callId: string) {
  return apiClient.post<never, ApiResult<null>>('/call/hangup', { callId })
}

export function signalCall(payload: CallSignalPayload) {
  return apiClient.post<never, ApiResult<null>>('/call/signal', payload)
}

export function reconnectCall(callId: string) {
  return apiClient.post<never, ApiResult<null>>('/call/reconnect', { callId })
}

export function switchCallDevice(callId: string, deviceType: 'audio' | 'video', enabled: boolean) {
  return apiClient.post<never, ApiResult<null>>('/call/switch-device', {
    callId,
    deviceType,
    enabled
  })
}
