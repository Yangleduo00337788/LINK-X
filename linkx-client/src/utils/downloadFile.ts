import { useAppSettingsStore } from '../stores/appSettings'

export type DownloadResult = {
  ok: boolean
  path?: string
  canceled?: boolean
  message?: string
}

/**
 * 按「文件管理」设置下载/保存文件。
 * - Electron：遵守下载目录 + 保存方式（询问 / 自动）
 * - Web：回退为浏览器默认下载
 */
export async function downloadFileWithSettings(
  url: string,
  fileName: string
): Promise<DownloadResult> {
  if (!url) {
    return { ok: false, message: '缺少文件地址' }
  }

  const settings = useAppSettingsStore()
  const directory = (settings.downloadPath || '').trim() || undefined
  const askEveryTime = !!settings.downloadAskEveryTime
  const name = (fileName || 'download').trim() || 'download'

  const api = window.electronAPI?.downloadFile
  if (api) {
    try {
      // http(s) 由主进程拉取，避免跨域；blob/data 等在渲染进程读入后传二进制
      if (/^https?:\/\//i.test(url)) {
        return await api({
          url,
          fileName: name,
          directory,
          askEveryTime
        })
      }

      const res = await fetch(url)
      if (!res.ok) {
        return { ok: false, message: `读取文件失败 (${res.status})` }
      }
      const data = await res.arrayBuffer()
      return await api({
        data,
        fileName: name,
        directory,
        askEveryTime
      })
    } catch (e) {
      return { ok: false, message: e instanceof Error ? e.message : '下载失败' }
    }
  }

  // Web：无法控制目录，使用浏览器下载
  try {
    const a = document.createElement('a')
    a.href = url
    a.download = name
    a.target = '_blank'
    a.rel = 'noopener'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    return { ok: true }
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : '下载失败' }
  }
}
