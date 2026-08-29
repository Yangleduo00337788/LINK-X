/**
 * 作者：yangleduo
 */
/**
 * 启动时版本检查：一次请求同时处理自动更新与「本次更新」弹窗。
 */

import type { DialogApi, MessageApi } from 'naive-ui'
import * as versionApi from '../api/version'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from './appVersion'
import { runStartupAutoUpdate } from './appUpdate'
import {
  getLastSeenWhatsNewVersion,
  type WhatsNewPayload
} from './whatsNew'

type TranslateFn = (key: string, params?: Record<string, unknown>) => string

export interface StartupVersionContext {
  message: MessageApi
  dialog: DialogApi
  t: TranslateFn
}

export interface StartupVersionResult {
  whatsNew: WhatsNewPayload
}

let startupFlowPromise: Promise<StartupVersionResult> | null = null

function resolveWhatsNewFromVersionInfo(info: versionApi.AppVersion): WhatsNewPayload {
  if (getLastSeenWhatsNewVersion() === APP_CLIENT_VERSION) {
    return { show: false, notes: '' }
  }
  if (info.hasUpdate) {
    return { show: false, notes: '' }
  }
  const notes = (info.currentReleaseNotes || '').trim()
  if (!notes) {
    return { show: false, notes: '' }
  }
  return { show: true, notes }
}

async function fetchStartupVersionResult(ctx: StartupVersionContext): Promise<StartupVersionResult> {
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION, APP_CLIENT_CHANNEL)
    if (res.code !== 200 || !res.data) {
      return { whatsNew: { show: false, notes: '' } }
    }
    const info = res.data
    if (info.hasUpdate) {
      runStartupAutoUpdate(info, ctx)
      return { whatsNew: { show: false, notes: '' } }
    }
    return { whatsNew: resolveWhatsNewFromVersionInfo(info) }
  } catch (e) {
    console.warn('[startupVersion] 启动版本检查失败:', e)
    return { whatsNew: { show: false, notes: '' } }
  }
}

/** 主界面挂载时调用，同一会话内只执行一次 */
export function runStartupVersionFlow(ctx: StartupVersionContext): Promise<StartupVersionResult> {
  if (!startupFlowPromise) {
    startupFlowPromise = fetchStartupVersionResult(ctx)
  }
  return startupFlowPromise
}

/** @internal 仅测试用 */
export function resetStartupVersionFlowForTests(): void {
  startupFlowPromise = null
}
