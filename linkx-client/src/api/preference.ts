/**
 * 用户偏好设置 API
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface UserPreference {
  autoStart: boolean
  soundNotify: boolean
  messageDetail: boolean
  notifyAtMe: boolean
  notifySound: boolean
  privacyVerifyFriend: boolean
  privacyAllowStranger: boolean
  privacyShowOnline: boolean
  language: string
  chatBackground: string
  notifyTone: string
  /** 友链背景图签名 URL */
  momentsBackground?: string
  favoritesViewMode?: string
  favoritesSort?: string
}

/**
 * 用户偏好 DTO（PUT 请求体）：所有字段可选；未传字段表示"不修改"。
 * 单个字段允许传 `null`（在 DTO 内部已被规范化为 undefined，由 service 层识别）。
 */
export type UserPreferencePatch = {
  [K in keyof UserPreference]?: UserPreference[K]
}

/**
 * 获取当前用户偏好设置。
 */
export function getPreference() {
  return apiClient.get<never, ApiResult<UserPreference>>('/user/preference')
}

/**
 * 局部更新当前用户偏好设置（PUT 语义）。
 */
export function updatePreference(patch: UserPreferencePatch) {
  return apiClient.put<never, ApiResult<UserPreference>>('/user/preference', patch)
}

/**
 * 上传友链背景图（返回更新后的完整偏好，包含签名 URL）
 */
export function uploadMomentsBackground(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<never, ApiResult<UserPreference>>('/user/moments-background', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}