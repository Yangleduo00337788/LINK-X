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

  it('checkUpdate 应调用 apiClient 并携带 channel', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      code: 200,
      data: {
        version: '1.0.1',
        currentVersion: '1.0.0',
        hasUpdate: true,
        forceUpdate: false,
        channel: 'beta',
        releaseNotes: 'notes',
        downloadUrl: '',
        supportEmail: 'help@linkx.test',
        supportPhone: '400-123-4567'
      }
    } as any)
    const res = await checkUpdate('1.0.0', 'beta')
    expect(apiClient.get).toHaveBeenCalledWith('/app/version', {
      params: { current: '1.0.0', channel: 'beta' }
    })
    expect(res.data?.supportEmail).toBe('help@linkx.test')
    expect(res.data?.supportPhone).toBe('400-123-4567')
  })

})
