import type { ApiResult, CaptchaData, LoginRequest, RegisterRequest, TokenData } from '../types/auth'
import { apiClient } from './client'

export function fetchCaptcha() {
  return apiClient.get<never, ApiResult<CaptchaData>>('/auth/captcha')
}

export function login(payload: LoginRequest) {
  return apiClient.post<never, ApiResult<TokenData>>('/auth/login', payload)
}

export function register(payload: RegisterRequest) {
  return apiClient.post<never, ApiResult<null>>('/auth/register', payload)
}

export function refreshToken(refreshToken: string) {
  return apiClient.post<never, ApiResult<TokenData>>('/auth/refresh', { refreshToken })
}

export function logout(refreshToken?: string | null) {
  return apiClient.post<never, ApiResult<null>>('/auth/logout', {
    refreshToken: refreshToken ?? undefined
  })
}
