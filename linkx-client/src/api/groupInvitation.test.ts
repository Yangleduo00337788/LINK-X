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
  listGroupInvitations,
  acceptGroupInvitation,
  rejectGroupInvitation,
  clearProcessedGroupInvitations,
  inviteToGroup
} from './groupInvitation'

describe('api/groupInvitation', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listGroupInvitations 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listGroupInvitations()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('acceptGroupInvitation 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await acceptGroupInvitation('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('rejectGroupInvitation 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await rejectGroupInvitation('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('clearProcessedGroupInvitations 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: 0 } as any)
    await clearProcessedGroupInvitations()
    expect(apiClient.delete).toHaveBeenCalledWith('/group/invitations/clear')
  })

  it('inviteToGroup 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await inviteToGroup('1', {} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

})
