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
  sendRedPacket,
  receiveRedPacket,
  getRedPacketDetail,
  listRedPackets
} from './redPacket'

describe('api/redPacket', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sendRedPacket 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await sendRedPacket({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('receiveRedPacket 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await receiveRedPacket('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('getRedPacketDetail 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getRedPacketDetail('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('listRedPackets 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listRedPackets('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

})
