import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface ConferenceCreatePayload {
  conversationId: string | number
  type?: 'voice' | 'video'
  title?: string
  password?: string
  maxParticipants?: number
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
  participants?: Array<Record<string, unknown>>
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
