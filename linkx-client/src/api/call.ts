import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface CallInviteResult {
  callId: string
  conversationId: string
  callType: 'voice' | 'video'
  status: string
}

export interface CallInvitePayload {
  conversationId: string
  callType: 'voice' | 'video'
}

/**
 * 发起语音/视频通话邀请
 */
export function inviteCall(payload: CallInvitePayload) {
  return apiClient.post<never, ApiResult<CallInviteResult>>('/call/invite', {
    conversationId: Number(payload.conversationId),
    callType: payload.callType
  })
}

/**
 * 取消通话邀请
 */
export function cancelCall(callId: string) {
  return apiClient.post<never, ApiResult<null>>('/call/cancel', { callId })
}
