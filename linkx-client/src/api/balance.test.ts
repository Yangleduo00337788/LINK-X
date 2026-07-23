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
  getBalance
} from './balance'

describe('api/balance', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getBalance 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getBalance()
    expect(apiClient.get).toHaveBeenCalled()
  })

})
