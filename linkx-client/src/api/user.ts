import type { ApiResult, UserInfo } from '../types/auth'
import { apiClient } from './client'

/**
 * 用户资料响应
 */
export interface UserProfileData extends UserInfo {
  createTime?: string
}

/**
 * 更新用户资料请求
 */
export interface UpdateProfileRequest {
  nickname?: string
  signature?: string
  gender?: string
  birthday?: string | number | null
  country?: string
  province?: string
  region?: string
}

/**
 * 获取当前登录用户信息
 */
export function getCurrentUser() {
  return apiClient.get<never, ApiResult<UserProfileData>>('/user/me')
}

/**
 * 更新用户资料
 */
export function updateProfile(payload: UpdateProfileRequest) {
  return apiClient.put<never, ApiResult<UserProfileData>>('/user/profile', payload)
}

/**
 * 上传用户头像
 * @param file 图片文件
 */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)

  return apiClient.post<never, ApiResult<string>>('/user/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取用户公开资料
 * @param userId 用户 ID
 */
export function getUserProfile(userId: string | number) {
  return apiClient.get<never, ApiResult<UserProfileData>>(`/user/${userId}/profile`)
}
