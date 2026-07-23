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
  fetchCaptcha,
  login,
  register,
  refreshToken,
  logout,
  fetchResetPasswordCaptcha,
  resetPassword,
  sendResetCode,
  resetPasswordByEmail
} from './auth'

describe('api/auth', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchCaptcha 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await fetchCaptcha()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('login 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await login({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('register 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await register({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('refreshToken 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await refreshToken('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('logout 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await logout()
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('fetchResetPasswordCaptcha 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await fetchResetPasswordCaptcha()
    expect(apiClient.post).toHaveBeenCalled()
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

  it('resetPasswordByEmail 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await resetPasswordByEmail({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

})
