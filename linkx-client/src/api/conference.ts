import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface ConferenceCreatePayload {
  conversationId: string | number
  type?: 'voice' | 'video'
  /** call=电话 meeting=会议 */
  scene?: 'call' | 'meeting'
  title?: string
  password?: string
  maxParticipants?: number
  lobbyEnabled?: boolean
}

export interface ConferenceParticipant {
  userId: string | number
  role?: string
  muted?: boolean
  videoOff?: boolean
  admitStatus?: number
  joinTime?: string
  nickname?: string
  avatar?: string
}

export interface ConferenceInfo {
  id: string | number
  title?: string
  type?: string
  scene?: 'call' | 'meeting' | string
  creatorId?: string | number
  conversationId?: string | number
  status?: number
  maxParticipants?: number
  startTime?: string
  endTime?: string
  callId?: string
  hasPassword?: boolean
  lobbyEnabled?: boolean
  waitingAdmit?: boolean
  reused?: boolean
  participants?: ConferenceParticipant[]
}

export function create(payload: ConferenceCreatePayload) {
  return apiClient.post<never, ApiResult<ConferenceInfo>>('/conference/create', payload)
}

export function join(conferenceId: string | number, password?: string) {
  return apiClient.post<never, ApiResult<ConferenceInfo>>('/conference/join', {
    conferenceId,
    password
  })
}

export function leave(conferenceId: string | number) {
  return apiClient.post<never, ApiResult<null>>('/conference/leave', { conferenceId })
}

export function end(conferenceId: string | number) {
  return apiClient.post<never, ApiResult<null>>('/conference/end', { conferenceId })
}

export function info(conferenceId: string | number) {
  return apiClient.get<never, ApiResult<ConferenceInfo>>(`/conference/info/${conferenceId}`)
}

export function active() {
  return apiClient.get<never, ApiResult<ConferenceInfo[]>>('/conference/active')
}

/** 会话内进行中会议（聊天顶栏），无则 data 为空 */
export function activeInConversation(conversationId: string | number) {
  return apiClient.get<never, ApiResult<ConferenceInfo | null>>('/conference/active-in-conversation', {
    params: { conversationId }
  })
}

export function history(conversationId: string | number) {
  return apiClient.get<never, ApiResult<ConferenceInfo[]>>('/conference/history', {
    params: { conversationId }
  })
}

export function mute(conferenceId: string | number, targetUserId: string | number, muted: boolean) {
  return apiClient.post<never, ApiResult<null>>('/conference/mute', {
    conferenceId,
    targetUserId,
    muted
  })
}

export function setVideo(conferenceId: string | number, videoOff: boolean) {
  return apiClient.post<never, ApiResult<null>>('/conference/video', {
    conferenceId,
    videoOff
  })
}

export function removeMember(conferenceId: string | number, targetUserId: string | number) {
  return apiClient.post<never, ApiResult<null>>('/conference/remove', {
    conferenceId,
    targetUserId
  })
}

export function transferHost(conferenceId: string | number, newHostId: string | number) {
  return apiClient.post<never, ApiResult<null>>('/conference/transfer-host', {
    conferenceId,
    newHostId
  })
}

export function admit(conferenceId: string | number, targetUserId: string | number) {
  return apiClient.post<never, ApiResult<null>>('/conference/admit', {
    conferenceId,
    targetUserId
  })
}

export function setRole(conferenceId: string | number, targetUserId: string | number, role: string) {
  return apiClient.post<never, ApiResult<null>>('/conference/set-role', {
    conferenceId,
    targetUserId,
    role
  })
}

export function raise(conferenceId: string | number, raised: boolean) {
  return apiClient.post<never, ApiResult<null>>('/conference/raise', {
    conferenceId,
    raised
  })
}

export interface ConferenceSignalPayload {
  conferenceId: string | number
  signalType: 'offer' | 'answer' | 'ice-candidate'
  sdp?: string
  candidate?: string
  targetUserId?: string | number
}

export function signal(payload: ConferenceSignalPayload) {
  return apiClient.post<never, ApiResult<null>>('/conference/signal', {
    conferenceId: String(payload.conferenceId),
    signalType: payload.signalType,
    sdp: payload.sdp,
    candidate: payload.candidate,
    // 雪花 ID 禁止 Number()，否则精度丢失导致对端收不到信令
    targetUserId: payload.targetUserId != null ? String(payload.targetUserId) : undefined
  })
}
