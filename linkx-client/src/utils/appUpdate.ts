/**
 * 作者：yangleduo
 */
/**
 * 应用检查更新与下载安装（设置页、侧栏更多菜单、启动时自动更新共用）
 */

import type { DialogApi, MessageApi } from 'naive-ui'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from './appVersion'
import * as versionApi from '../api/version'
import { resolveAppDownloadUrl } from './resolveAppDownloadUrl'

type TranslateFn = (key: string, params?: Record<string, unknown>) => string

export interface AppUpdateContext {
  message: MessageApi
  dialog: DialogApi
  t: TranslateFn
  onProgress?: (updating: boolean, progressText?: string) => void
}

export interface DownloadUpdateOptions {
  install?: boolean
  silent?: boolean
  onProgress?: (phase?: string, percent?: number) => void
}

type PendingUpdate = {
  version: string
  path: string
  silent: boolean
}

let pendingUpdate: PendingUpdate | null = null
let startupAutoUpdateStarted = false

function toUpdatePayload(info: versionApi.AppVersion) {
  return {
    version: info.version,
    downloadUrl: info.downloadUrl,
    releaseNotes: info.releaseNotes,
    packageSha256: info.packageSha256,
    packageFileName: info.packageFileName
  }
}

export async function downloadUpdatePackage(
  info: {
    version: string
    downloadUrl?: string
    localPath?: string
    packageSha256?: string
    packageFileName?: string
  },
  options: DownloadUpdateOptions = {}
): Promise<{
  ok: boolean
  path?: string
  ready?: boolean
  launched?: boolean
  silent?: boolean
  message?: string
}> {
  const install = options.install !== false
  const silent = options.silent !== false
  const url = info.localPath ? '' : resolveAppDownloadUrl(info.downloadUrl || '')
  if (!info.localPath && !url) {
    return { ok: false, message: 'no-download-url' }
  }

  const unsub = window.electronAPI?.onUpdateProgress?.(data => {
    options.onProgress?.(data.phase, data.percent)
  })

  try {
    if (!window.electronAPI?.downloadAndInstallUpdate) {
      return { ok: false, message: 'no-electron' }
    }
    const result = await window.electronAPI.downloadAndInstallUpdate({
      url: url || undefined,
      localPath: info.localPath,
      version: info.version,
      fileName: info.packageFileName,
      sha256: info.packageSha256,
      silent,
      install
    })
    if (result.ok && result.path && !install) {
      pendingUpdate = { version: info.version, path: result.path, silent }
    }
    return result
  } finally {
    unsub?.()
  }
}

export async function installPendingUpdate(
  ctx: Pick<AppUpdateContext, 'message' | 't'>
): Promise<boolean> {
  if (!pendingUpdate) return false
  const current = pendingUpdate
  pendingUpdate = null
  const result = await downloadUpdatePackage(
    { version: current.version, localPath: current.path },
    { install: true, silent: current.silent }
  )
  if (!result.ok) {
    ctx.message.error(result.message || ctx.t('about.installFail'))
    pendingUpdate = current
    return false
  }
  if (result.launched && result.silent) {
    ctx.message.info(ctx.t('about.silentInstallHint'))
  } else if (result.launched) {
    ctx.message.info(ctx.t('about.installStarted'))
  }
  return true
}

async function startDownloadAndInstall(
  info: ReturnType<typeof toUpdatePayload>,
  ctx: AppUpdateContext
) {
  const url = resolveAppDownloadUrl(info.downloadUrl || '')
  if (!url) {
    ctx.message.warning(ctx.t('about.noDownloadUrl'))
    return
  }

  ctx.onProgress?.(true, ctx.t('about.downloading'))

  try {
    if (window.electronAPI?.downloadAndInstallUpdate) {
      const result = await downloadUpdatePackage(info, {
        install: true,
        silent: true,
        onProgress: phase => {
          ctx.onProgress?.(
            true,
            phase === 'installing' ? ctx.t('about.installing') : ctx.t('about.downloading')
          )
        }
      })
      if (!result.ok) {
        ctx.message.error(result.message || ctx.t('about.installFail'))
        return
      }
      if (result.launched && result.silent) {
        ctx.onProgress?.(true, ctx.t('about.silentInstallHint'))
        return
      }
      if (result.launched) {
        ctx.onProgress?.(true, ctx.t('about.installStarted'))
        return
      }
      ctx.message.info(result.message || ctx.t('about.downloadReady'))
      return
    }

    const a = document.createElement('a')
    a.href = url
    a.target = '_blank'
    a.rel = 'noopener'
    a.download = ''
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    ctx.message.success(ctx.t('about.webDownloadStarted'))
  } catch (e) {
    console.warn('[appUpdate] 下载安装失败:', e)
    ctx.message.error(ctx.t('about.installFail'))
  } finally {
    if (!window.electronAPI?.downloadAndInstallUpdate) {
      ctx.onProgress?.(false)
    }
  }
}

function showUpdateDialog(info: versionApi.AppVersion, ctx: AppUpdateContext) {
  const notes = (info.releaseNotes || '').trim()
  const force = info.forceUpdate === true
  ctx.dialog.warning({
    title: force ? ctx.t('about.forceUpdateTitle') : ctx.t('about.updateTitle'),
    content:
      ctx.t('about.found', { version: info.version, notes: notes || ctx.t('about.noNotes') }) +
      '\n\n' +
      (force ? ctx.t('about.forceUpdateHint') : ctx.t('about.autoInstallHint')),
    positiveText: ctx.t('about.downloadInstall'),
    negativeText: force ? undefined : ctx.t('common.cancel'),
    closable: !force,
    maskClosable: !force,
    closeOnEsc: !force,
    onPositiveClick: () => {
      void startDownloadAndInstall(toUpdatePayload(info), ctx)
    }
  })
}

function showUpdateReadyDialog(
  info: versionApi.AppVersion,
  ctx: Pick<AppUpdateContext, 'dialog' | 'message' | 't'>
) {
  const notes = (info.releaseNotes || '').trim()
  ctx.dialog.info({
    title: ctx.t('about.updateReadyTitle', { version: info.version }),
    content: notes || ctx.t('about.updateReadyHint'),
    positiveText: ctx.t('about.updateReadyInstall'),
    negativeText: ctx.t('about.updateReadyLater'),
    onPositiveClick: () => {
      void installPendingUpdate(ctx)
    }
  })
}

/** 启动时静默检查并后台下载；强制更新则下载完成后自动安装 */
export function runStartupAutoUpdate(
  info: versionApi.AppVersion,
  ctx: Pick<AppUpdateContext, 'message' | 'dialog' | 't'>
): void {
  if (startupAutoUpdateStarted || !info.hasUpdate) return
  if (!window.electronAPI?.downloadAndInstallUpdate) return
  if (!resolveAppDownloadUrl(info.downloadUrl || '')) return

  startupAutoUpdateStarted = true
  const force = info.forceUpdate === true
  const payload = toUpdatePayload(info)

  if (force) {
    ctx.message.loading(ctx.t('about.forceUpdateDownloading'), { duration: 0 })
  }

  void downloadUpdatePackage(payload, {
    install: force,
    silent: true,
    onProgress: phase => {
      if (phase === 'installing' && force) {
        ctx.message.destroyAll()
        ctx.message.info(ctx.t('about.silentInstallHint'))
      }
    }
  })
    .then(result => {
      if (force) {
        ctx.message.destroyAll()
      }
      if (!result.ok) {
        console.warn('[appUpdate] 启动时自动更新失败:', result.message)
        if (force) {
          showUpdateDialog(info, ctx as AppUpdateContext)
        }
        return
      }
      if (result.ready) {
        showUpdateReadyDialog(info, ctx)
      }
    })
    .catch(e => {
      if (force) ctx.message.destroyAll()
      console.warn('[appUpdate] 启动时自动更新异常:', e)
      if (force) {
        showUpdateDialog(info, ctx as AppUpdateContext)
      }
    })
}

/** 检查更新；无新版本时 toast 提示，有更新时弹窗引导下载 */
export async function checkAppUpdate(ctx: AppUpdateContext): Promise<void> {
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION, APP_CLIENT_CHANNEL)
    if (res.code !== 200 || !res.data) {
      ctx.message.error(res.message || ctx.t('about.checkFail'))
      return
    }
    const info = res.data
    if (!info.hasUpdate) {
      ctx.message.success(ctx.t('about.latest', { version: info.version }))
      return
    }
    showUpdateDialog(info, ctx)
  } catch (e) {
    console.warn('[appUpdate] 检查更新失败:', e)
    ctx.message.error(ctx.t('about.checkFailRetry'))
  }
}
