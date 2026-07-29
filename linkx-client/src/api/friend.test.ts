import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  }
}))

import { apiClient } from './client'
import {
  searchUsers,
  sendFriendRequest,
  listIncomingRequests,
  listOutgoingRequests,
  acceptFriendRequest,
  rejectFriendRequest,
  listFriends,
  deleteFriend,
  updateFriendRemark,
  updateFriendGroup,
  blockFriend,
  unblockFriend
} from './friend'

describe('api/friend', () => {
  beforeEach(() => vi.clearAllMocks())

  it('searchUsers 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await searchUsers('x')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('sendFriendRequest 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await sendFriendRequest({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('listIncomingRequests 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listIncomingRequests()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('listOutgoingRequests 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listOutgoingRequests()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('acceptFriendRequest 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await acceptFriendRequest({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('rejectFriendRequest 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await rejectFriendRequest({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('listFriends 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listFriends()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('deleteFriend 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteFriend('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('updateFriendRemark 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: '备注' } as any)
    await updateFriendRemark('1', '备注')
    expect(apiClient.put).toHaveBeenCalledWith('/friend/1/remark', { remark: '备注' })
  })

  it('updateFriendGroup 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: '同事' } as any)
    await updateFriendGroup('1', '同事')
    expect(apiClient.put).toHaveBeenCalledWith('/friend/1/group', { groupName: '同事' })
  })

  it('blockFriend 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await blockFriend('1')
    expect(apiClient.post).toHaveBeenCalledWith('/friend/1/block')
  })

  it('unblockFriend 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await unblockFriend('1')
    expect(apiClient.post).toHaveBeenCalledWith('/friend/1/unblock')
  })

})
