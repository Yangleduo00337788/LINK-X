import { get, post, setTokens, clearTokens } from './request'
import type {
  AdminLoginResult,
  AdminMenuTree,
  AdminUserProfile,
  AuthConfigVO,
  CaptchaVO,
} from '@/types/api'

export interface LoginPayload {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

export function fetchCaptcha() {
  return get<CaptchaVO>('/auth/captcha')
}

export function fetchAuthConfig() {
  return get<AuthConfigVO>('/auth/config')
}

export async function login(payload: LoginPayload) {
  const data = await post<AdminLoginResult>('/admin/auth/login', payload)
  setTokens(data.accessToken, data.refreshToken)
  return data
}

export async function logout(refreshToken?: string) {
  try {
    await post<null>('/admin/auth/logout', refreshToken ? { refreshToken } : {})
  } finally {
    clearTokens()
  }
}

export function fetchMe() {
  return get<AdminUserProfile>('/admin/auth/me')
}

export function fetchMenus() {
  return get<AdminMenuTree[]>('/admin/auth/menus')
}

export function fetchPermissions() {
  return get<string[]>('/admin/auth/permissions')
}
