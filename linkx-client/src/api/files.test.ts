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
  listCloudFiles
} from './files'

describe('api/files', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listCloudFiles 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listCloudFiles()
    expect(apiClient.get).toHaveBeenCalled()
  })

})
