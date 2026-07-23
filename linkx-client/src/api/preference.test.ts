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
  getPreference,
  updatePreference,
  uploadMomentsBackground
} from './preference'

describe('api/preference', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getPreference 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getPreference()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('updatePreference 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updatePreference('1')
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('uploadMomentsBackground 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await uploadMomentsBackground(new File(['x'], 'a.bin'))
    expect(apiClient.post).toHaveBeenCalled()
  })

})
