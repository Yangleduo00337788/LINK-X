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
  listGroups,
  getGroupInfo,
  createGroup,
  updateGroup,
  listGroupMembers,
  addGroupMembers,
  removeGroupMember,
  quitGroup,
  dissolveGroup,
  transferGroupOwner,
  updateMemberRole,
  updateMuteAll,
  updateMemberMute,
  updateGroupRemark,
  requestJoin,
  listJoinRequests,
  handleJoinRequest
} from './group'

describe('api/group', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listGroups 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listGroups()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('getGroupInfo 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getGroupInfo('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('createGroup 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createGroup({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('updateGroup 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateGroup('1', {} as any)
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('listGroupMembers 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listGroupMembers('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('addGroupMembers 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await addGroupMembers('1', {} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('removeGroupMember 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await removeGroupMember('1', '1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('quitGroup 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await quitGroup('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('dissolveGroup 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await dissolveGroup('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('transferGroupOwner 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await transferGroupOwner('1', '1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('updateMemberRole 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateMemberRole('1', '1', '1')
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('updateMuteAll 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateMuteAll('1')
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('updateMemberMute 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateMemberMute('1', '1')
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('updateGroupRemark 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateGroupRemark('1', '1')
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('requestJoin 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await requestJoin('1', 'hi')
    expect(apiClient.post).toHaveBeenCalledWith('/group/1/join-request', { message: 'hi' })
  })

  it('listJoinRequests 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: [] } as any)
    await listJoinRequests('1')
    expect(apiClient.get).toHaveBeenCalledWith('/group/1/join-requests')
  })

  it('handleJoinRequest 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await handleJoinRequest('1', '2', true)
    expect(apiClient.post).toHaveBeenCalledWith('/group/1/join-request/2', { approve: true })
  })

})
