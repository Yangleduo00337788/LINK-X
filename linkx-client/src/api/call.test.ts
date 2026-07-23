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
  inviteCall,
  cancelCall,
  acceptCall,
  rejectCall,
  hangupCall,
  signalCall
} from './call'

describe('api/call', () => {
  beforeEach(() => vi.clearAllMocks())

  it('inviteCall 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await inviteCall({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('cancelCall 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await cancelCall('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('acceptCall 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await acceptCall('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('rejectCall 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await rejectCall('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('hangupCall 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await hangupCall('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('signalCall 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await signalCall({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

})
