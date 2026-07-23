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
  getCurrentUser,
  updateProfile,
  uploadAvatar,
  getUserProfile
} from './user'

describe('api/user', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getCurrentUser 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getCurrentUser()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('updateProfile 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateProfile({} as any)
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('uploadAvatar 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await uploadAvatar(new File(['x'], 'a.bin'))
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('getUserProfile 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getUserProfile(10)
    expect(apiClient.get).toHaveBeenCalled()
  })

})
