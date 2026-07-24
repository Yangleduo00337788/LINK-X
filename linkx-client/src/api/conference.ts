import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface ConferenceCreatePayload {
  conversationId: string | number
  type?: 'voice' | 'video'
  title?: string
  password?: string
  maxParticipants?: number
}

export interface ConferenceParticipant {
  userId: string | number
  role?: string
  muted?: boolean
  videoOff?: boolean
  joinTime?: string
  nickname?: string
  avatar?: string
}

export interface ConferenceInfo {
  id: string | number
  title?: string
  type?: string
  creatorId?: string | number
  conversationId?: string | number
  status?: number
  maxParticipants?: number
  startTime?: string
  endTime?: string
  callId?: string
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
