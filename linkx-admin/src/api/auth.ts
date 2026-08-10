/**
 * 作者：yangleduo
 */
import { get, post, put, clearTokens } from './request'
import type {
  AdminLoginResult,
  AdminMenuTree,
  AdminTotpSetup,
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
  return get<CaptchaVO>('/admin/auth/captcha')
}

export function fetchAuthConfig() {
  return get<AuthConfigVO>('/admin/auth/config')
}

/** 密码登录；若开启 2FA 则返回 challenge，不写 token */
export async function login(payload: LoginPayload) {
  return post<AdminLoginResult>('/admin/auth/login', payload)
}

export async function verifyTotpLogin(challengeToken: string, code: string) {
  return post<AdminLoginResult>('/admin/auth/login/totp', { challengeToken, code })
}

export function beginTotpSetupChallenge(challengeToken: string) {
  return post<AdminTotpSetup>('/admin/auth/totp/setup-challenge', { challengeToken })
}

export async function confirmTotpChallenge(challengeToken: string, code: string) {
  return post<AdminLoginResult>('/admin/auth/totp/confirm-challenge', {
    challengeToken,
    code,
  })
}

export function beginTotpSetup() {
  return post<AdminTotpSetup>('/admin/auth/totp/setup')
}

export function confirmTotp(code: string) {
  return post<AdminUserProfile>('/admin/auth/totp/confirm', { code })
}

export function disableTotp(password: string, code: string) {
  return post<AdminUserProfile>('/admin/auth/totp/disable', { password, code })
}

export async function logout() {
  try {
    await post<null>('/admin/auth/logout', {})
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

export interface AdminProfileUpdatePayload {
  nickname?: string
  avatar?: string
  email?: string | null
}

export function updateProfile(payload: AdminProfileUpdatePayload) {
  return put<AdminUserProfile>('/admin/auth/profile', payload)
}

/** 上传头像（复用用户端接口，管理端 JWT 可用）；返回可展示的预签名 URL */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<string>('/user/avatar', formData)
}

/** 修改当前登录账号密码（复用用户端接口，管理端 JWT 可用） */
export function changePassword(oldPassword: string, newPassword: string) {
  return post<null>('/user/change-password', { oldPassword, newPassword })
}
