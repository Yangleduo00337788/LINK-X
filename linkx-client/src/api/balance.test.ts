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
  getBalance,
  listBalanceLogs,
  rechargeBalance
} from './balance'

describe('api/balance', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getBalance 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getBalance()
    expect(apiClient.get).toHaveBeenCalledWith('/balance')
  })

  it('listBalanceLogs 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: [] } as any)
    await listBalanceLogs({ limit: 20 })
    expect(apiClient.get).toHaveBeenCalledWith('/balance/logs', { params: { limit: 20 } })
  })

  it('rechargeBalance 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await rechargeBalance(10)
    expect(apiClient.post).toHaveBeenCalledWith('/balance/recharge', { amount: 10 })
  })
})
