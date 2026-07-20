/**
 * 账号安全 API
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'
import type { UserProfileData } from './user'

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

export function changePassword(payload: ChangePasswordPayload) {
  return apiClient.post<never, ApiResult<null>>('/user/change-password', payload)
}

export function listDevices() {
  return apiClient.get<never, ApiResult<DeviceInfo[]>>('/user/devices')
}

export function logoutDevice(deviceId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/user/devices/${deviceId}`)
}

export function getCurrentUser() {
  return apiClient.get<never, ApiResult<UserProfileData>>('/user/me')
}

export function resetPassword(payload: ResetPasswordPayload) {
  return apiClient.post<never, ApiResult<null>>('/auth/reset-password', payload)
}

export function sendResetCode(payload: SendResetCodePayload) {
  return apiClient.post<never, ApiResult<null>>('/auth/send-reset-code', payload)
}

export function verifyResetCode(payload: { username: string; code: string }) {
  return apiClient.post<never, ApiResult<null>>('/auth/verify-reset-code', payload)
}

export function resetPasswordByEmail(payload: ResetPasswordByEmailPayload) {
  return apiClient.post<never, ApiResult<null>>('/auth/reset-password-by-email', payload)
}

export function sendBindEmailCode(email: string) {
  return apiClient.post<never, ApiResult<null>>('/user/bind-email/send-code', { email })
}

export function bindEmail(payload: { email: string; code: string }) {
  return apiClient.post<never, ApiResult<UserProfileData>>('/user/bind-email', payload)
}

export function bindPhone(payload: { phone: string; password: string }) {
  return apiClient.post<never, ApiResult<UserProfileData>>('/user/bind-phone', payload)
}

export function deleteAccount(payload: { password: string }) {
  return apiClient.post<never, ApiResult<null>>('/user/delete-account', payload)
}
