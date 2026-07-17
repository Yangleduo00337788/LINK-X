/**
 * 账号安全 API
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export interface DeviceInfo {
  id: string
  deviceName: string
  deviceType: string
  lastActive: string
  current: boolean
}

export interface ResetPasswordPayload {
  username: string
  captchaId: string
  captchaCode: string
  newPassword: string
}

export interface SendResetCodePayload {
  username: string
}

export interface ResetPasswordByEmailPayload {
  username: string
  code: string
  newPassword: string
}

/**
 * 修改密码
 */
export function changePassword(payload: ChangePasswordPayload) {
  return apiClient.post<never, ApiResult<null>>('/user/change-password', payload)
}

/**
 * 获取登录设备列表
 */
export function listDevices() {
  return apiClient.get<never, ApiResult<DeviceInfo[]>>('/user/devices')
}

/**
 * 强制下线指定设备
 */
export function logoutDevice(deviceId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/user/devices/${deviceId}`)
}

/**
 * 获取当前用户信息
 */
export function getCurrentUser() {
  return apiClient.get<never, ApiResult<{ userId: string; username: string; nickname: string; avatar: string }>>('/user/me')
}

/**
 * 重置密码（通过验证码，需已登录）
 */
export function resetPassword(payload: ResetPasswordPayload) {
  return apiClient.post<never, ApiResult<null>>('/auth/reset-password', payload)
}

/**
 * 发送密码重置邮件验证码
 */
export function sendResetCode(payload: SendResetCodePayload) {
  return apiClient.post<never, ApiResult<null>>('/auth/send-reset-code', payload)
}

/**
 * 仅校验邮箱验证码（不消费），用于分步表单的「下一步」按钮
 */
export function verifyResetCode(payload: { username: string; code: string }) {
  return apiClient.post<never, ApiResult<null>>('/auth/verify-reset-code', payload)
}

/**
 * 通过邮箱验证码重置密码
 */
export function resetPasswordByEmail(payload: ResetPasswordByEmailPayload) {
  return apiClient.post<never, ApiResult<null>>('/auth/reset-password-by-email', payload)
}
