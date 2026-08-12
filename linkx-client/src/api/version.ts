/**
 * 作者：yangleduo
 */
/**
 * 应用版本 API（"检查更新"）
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'
import { APP_CLIENT_CHANNEL, getClientPlatform, type AppClientPlatform } from '../utils/appVersion'

export interface AppVersion {
  /** 服务端最新版本号 */
  version: string
  /** 客户端当前版本号（来自请求参数） */
  currentVersion: string
  /** 是否需要升级 */
  hasUpdate: boolean
  /** 是否强制升级 */
  forceUpdate: boolean
  /** 发布渠道 */
  channel: string
  /** 目标平台 */
  platform?: string
  /** 升级提示/已是最新提示 */
  releaseNotes: string
  /** 下载地址（可空） */
  downloadUrl: string
  /** 安装包 SHA-256 */
  packageSha256?: string
  /** 安装包文件名 */
  packageFileName?: string
  /** 客服邮箱（可空） */
  supportEmail?: string
  /** 客服电话（可空） */
  supportPhone?: string
}

/**
 * 检查更新
 * @param current 客户端当前版本号
 * @param channel 客户端渠道（默认构建渠道）
 */
export function checkUpdate(
  current: string,
  channel: string = APP_CLIENT_CHANNEL,
  platform: AppClientPlatform = getClientPlatform()
) {
  return apiClient.get<never, ApiResult<AppVersion>>('/app/version', {
    params: { current, channel, platform }
  })
}
