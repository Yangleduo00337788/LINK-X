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
  listGroupAnnouncements,
  getDisplayAnnouncement,
  createGroupAnnouncement,
  updateGroupAnnouncement,
  deleteGroupAnnouncement
} from './groupAnnouncement'

describe('api/groupAnnouncement', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listGroupAnnouncements 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listGroupAnnouncements('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('getDisplayAnnouncement 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getDisplayAnnouncement('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('createGroupAnnouncement 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createGroupAnnouncement('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('updateGroupAnnouncement 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateGroupAnnouncement('1', '1')
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('deleteGroupAnnouncement 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteGroupAnnouncement('1', '1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

})
