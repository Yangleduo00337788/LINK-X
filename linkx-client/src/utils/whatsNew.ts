/**
 * 作者：yangleduo
 */
/**
 * 「本次更新」弹窗：从服务端拉取管理端发布的更新说明，本地记录已读版本。
 */

import * as versionApi from '../api/version'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from './appVersion'

const STORAGE_KEY = 'lx-whats-new-seen-version'

export interface WhatsNewPayload {
  show: boolean
  notes: string
}

export function getLastSeenWhatsNewVersion(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function markWhatsNewSeen(version: string = APP_CLIENT_VERSION): void {
  try {
    localStorage.setItem(STORAGE_KEY, version)
  } catch {
    /* ignore quota / private mode */
  }
}

/** 将管理端填写的多行更新说明解析为可渲染块 */
export type ReleaseNoteBlock =
  | { kind: 'text'; content: string }
  | { kind: 'list'; items: string[] }

export function parseReleaseNotes(notes: string): ReleaseNoteBlock[] {
  const lines = notes
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)

  const blocks: ReleaseNoteBlock[] = []
  let pendingItems: string[] = []

  const flushItems = () => {
    if (!pendingItems.length) return
    blocks.push({ kind: 'list', items: [...pendingItems] })
    pendingItems = []
  }

  for (const line of lines) {
    const bullet = line.match(/^[-*•]\s+(.+)/)
    if (bullet) {
      pendingItems.push(bullet[1])
      continue
    }
    flushItems()
    blocks.push({ kind: 'text', content: line })
  }
  flushItems()
  return blocks
}

/**
 * 进入主界面时调用：已是最新版本且服务端有当前版本的更新说明时展示弹窗。
 * 若仍有可升级版本，优先走检查更新流程，不展示「本次更新」。
 * @deprecated 请使用 runStartupVersionFlow，避免重复请求 /app/version
 */
export async function resolveWhatsNew(
  version: string = APP_CLIENT_VERSION
): Promise<WhatsNewPayload> {
  if (getLastSeenWhatsNewVersion() === version) {
    return { show: false, notes: '' }
  }
  try {
    const res = await versionApi.checkUpdate(version, APP_CLIENT_CHANNEL)
    if (res.code !== 200 || !res.data) {
      return { show: false, notes: '' }
    }
    const info = res.data
    if (info.hasUpdate) {
      return { show: false, notes: '' }
    }
    const notes = (info.currentReleaseNotes || '').trim()
    if (!notes) {
      return { show: false, notes: '' }
    }
    return { show: true, notes }
  } catch (e) {
    console.warn('[whatsNew] 拉取更新说明失败:', e)
    return { show: false, notes: '' }
  }
}
