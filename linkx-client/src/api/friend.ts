import type { ApiResult } from '../types/auth'
import type {
  FriendItem,
  FriendRequestItem,
  SendFriendRequestPayload,
  UserSearchResult
} from '../types/friend'
import { apiClient } from './client'

export function searchUsers(keyword: string) {
  return apiClient.get<never, ApiResult<UserSearchResult[]>>('/friend/search', {
    params: { keyword }
  })
}

export function sendFriendRequest(payload: SendFriendRequestPayload) {
  return apiClient.post<never, ApiResult<null>>('/friend/request', payload)
}

export function listIncomingRequests() {
  return apiClient.get<never, ApiResult<FriendRequestItem[]>>('/friend/requests/incoming')
}

export function listOutgoingRequests() {
  return apiClient.get<never, ApiResult<FriendRequestItem[]>>('/friend/requests/outgoing')
}

export function acceptFriendRequest(requestId: string) {
  return apiClient.post<never, ApiResult<null>>(`/friend/requests/${requestId}/accept`)
}

export function rejectFriendRequest(requestId: string) {
  return apiClient.post<never, ApiResult<null>>(`/friend/requests/${requestId}/reject`)
}

export function listFriends() {
  return apiClient.get<never, ApiResult<FriendItem[]>>('/friend/list')
}

export function deleteFriend(friendId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/friend/${friendId}`)
}
