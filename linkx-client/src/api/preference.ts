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