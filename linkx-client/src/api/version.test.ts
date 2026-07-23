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
  checkUpdate
} from './version'

describe('api/version', () => {
  beforeEach(() => vi.clearAllMocks())

  it('checkUpdate 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await checkUpdate('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

})
