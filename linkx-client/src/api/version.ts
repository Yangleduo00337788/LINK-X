/**
 * 应用版本 API（"检查更新"）
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface AppVersion {
  /** 服务端最新版本号 */
  version: string
  /** 客户端当前版本号（来自请求参数） */
  currentVersion: string
  /** 是否需要升级 */
  hasUpdate: boolean
  /** 是否强制升级（保留字段） */
  forceUpdate: boolean
  /** 发布渠道 */
  channel: string
  /** 升级提示/已是最新提示 */
  releaseNotes: string
  /** 下载地址（可空） */
  downloadUrl: string
}

/**
 * 检查更新
 * @param current 客户端当前版本号
 */
export function checkUpdate(current: string) {
  return apiClient.get<never, ApiResult<AppVersion>>('/app/version', {
    params: { current }
  })
}