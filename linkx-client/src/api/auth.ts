import type {
  ApiResult,
  CaptchaData,
  LoginRequest,
  RegisterRequest,
  ResetPasswordByEmailRequest,
  ResetPasswordRequest,
  SendResetCodeRequest,
  TokenData
} from '../types/auth'
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

/**
 * 生成"已登录态"重置密码的图形验证码（{@code POST /auth/reset-password-captcha}）。
 * 验证码与当前 token 内的 userId 绑定，防止横向越权。
 */
export function fetchResetPasswordCaptcha() {
  return apiClient.post<never, ApiResult<CaptchaData>>('/auth/reset-password-captcha')
}

/**
 * 已登录用户用图形验证码修改密码（{@code POST /auth/reset-password}）。
 */
export function resetPassword(payload: ResetPasswordRequest) {
  return apiClient.post<never, ApiResult<null>>('/auth/reset-password', payload)
}

/**
 * 通过用户名发送邮箱验证码（{@code POST /auth/send-reset-code}），用于忘记密码场景。
 */
export function sendResetCode(payload: SendResetCodeRequest) {
  return apiClient.post<never, ApiResult<null>>('/auth/send-reset-code', payload)
}

/**
 * 通过邮箱验证码重置密码（{@code POST /auth/reset-password-by-email}）。
 */
export function resetPasswordByEmail(payload: ResetPasswordByEmailRequest) {
  return apiClient.post<never, ApiResult<null>>('/auth/reset-password-by-email', payload)
}
