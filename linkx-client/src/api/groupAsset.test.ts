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
  listGroupAssets,
  uploadGroupAsset,
  createGroupEssence,
  deleteGroupAsset
} from './groupAsset'

describe('api/groupAsset', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listGroupAssets 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listGroupAssets('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('uploadGroupAsset 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await uploadGroupAsset('1', 'x', new File(['x'], 'a.bin'))
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('createGroupEssence 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createGroupEssence('1', {} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('deleteGroupAsset 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteGroupAsset('1', '1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

})
