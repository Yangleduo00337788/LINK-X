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
  return apiClient.post<never, ApiResult<CallInviteResult>>('/call/invite', {
    conversationId: Number(payload.conversationId),
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
