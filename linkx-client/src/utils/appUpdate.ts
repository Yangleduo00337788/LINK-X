/**
 * 作者：yangleduo
 */
/**
 * 应用检查更新与下载安装（设置页、侧栏更多菜单共用）
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

async function startDownloadAndInstall(
  info: {
    version: string
    downloadUrl: string
    releaseNotes?: string
    packageSha256?: string
    packageFileName?: string
  },
  ctx: AppUpdateContext
) {
  const url = resolveAppDownloadUrl(info.downloadUrl || '')
  if (!url) {
    ctx.message.warning(ctx.t('about.noDownloadUrl'))
    return
  }

  ctx.onProgress?.(true, ctx.t('about.downloading'))

  const unsub = window.electronAPI?.onUpdateProgress?.(data => {
    ctx.onProgress?.(
      true,
      data.phase === 'installing' ? ctx.t('about.installing') : ctx.t('about.downloading')
    )
  })

  try {
    if (window.electronAPI?.downloadAndInstallUpdate) {
      const result = await window.electronAPI.downloadAndInstallUpdate({
        url,
        version: info.version,
        fileName: info.packageFileName,
        sha256: info.packageSha256,
        silent: true
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
    unsub?.()
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
      void startDownloadAndInstall(info, ctx)
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
