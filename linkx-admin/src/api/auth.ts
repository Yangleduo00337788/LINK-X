import { get, post, put, setTokens, clearTokens } from './request'
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
  return get<AuthConfigVO>('/admin/auth/config')
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
