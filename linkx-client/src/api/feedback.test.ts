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
  submitFeedback,
  listFeedback
} from './feedback'

describe('api/feedback', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submitFeedback 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await submitFeedback({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('listFeedback 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listFeedback()
    expect(apiClient.get).toHaveBeenCalled()
  })

})
