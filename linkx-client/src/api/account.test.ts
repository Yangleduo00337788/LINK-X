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
  changePassword,
  listDevices,
  logoutDevice,
  getCurrentUser,
  resetPassword,
  sendResetCode,
  verifyResetCode,
  resetPasswordByEmail,
  sendBindEmailCode,
  bindEmail,
  bindPhone,
  deleteAccount
} from './account'

describe('api/account', () => {
  beforeEach(() => vi.clearAllMocks())

  it('changePassword 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await changePassword({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('listDevices 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listDevices()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('logoutDevice 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await logoutDevice('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('getCurrentUser 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getCurrentUser()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('resetPassword 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await resetPassword({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('sendResetCode 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await sendResetCode({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('verifyResetCode 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await verifyResetCode({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('resetPasswordByEmail 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await resetPasswordByEmail({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('sendBindEmailCode 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await sendBindEmailCode('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('bindEmail 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await bindEmail({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('bindPhone 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await bindPhone({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('deleteAccount 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await deleteAccount({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

})
