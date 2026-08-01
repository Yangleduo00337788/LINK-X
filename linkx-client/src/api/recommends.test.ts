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
import { listRecommends } from './recommends'
import { listActivities } from './activities'

describe('api/ops content', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listRecommends 携带 slotCode', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: [] } as any)
    await listRecommends('discover')
    expect(apiClient.get).toHaveBeenCalledWith('/app/recommends', {
      params: { slotCode: 'discover' }
    })
  })

  it('listActivities 调用正确路径', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: [] } as any)
    await listActivities()
    expect(apiClient.get).toHaveBeenCalledWith('/app/activities')
  })
})
