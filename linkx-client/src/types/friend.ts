export interface UserSearchResult {
  id: string
  username: string
  nickname: string
  avatar?: string
}

export interface FriendItem {
  userId: string
  username: string
  nickname: string
  avatar?: string
  remark?: string
}

export interface FriendRequestItem {
  id: string
  fromUserId: string
  toUserId: string
  fromUsername: string
  fromNickname: string
  fromAvatar?: string
  peerUserId?: string
  peerUsername: string
  peerNickname: string
  peerAvatar?: string
  message?: string
  /** 0=待处理 1=已同意 2=已拒绝 */
  status: 0 | 1 | 2
  direction: 'incoming' | 'outgoing'
  createTime: string
}

export interface SendFriendRequestPayload {
  username: string
  message?: string
}
