import { useAppSettingsStore } from '../stores/appSettings'
import { t } from '../i18n'

export type DownloadResult = {
  ok: boolean
  path?: string
  canceled?: boolean
  message?: string
}

export type DownloadOptions = {
  /** 保存后用系统默认程序打开（仅 Electron） */
  openAfter?: boolean
  /** 已在渲染进程读好的二进制（blob/裁剪结果），优先于 url 拉取 */
  data?: ArrayBuffer | Uint8Array | Blob
}

/** dataURL → ArrayBuffer（不走 fetch，避免 Electron 下失败） */
function dataUrlToArrayBuffer(dataUrl: string): ArrayBuffer {
  const comma = dataUrl.indexOf(',')
  if (comma < 0) throw new Error(t('errors.invalidDataUrl'))
  const meta = dataUrl.slice(0, comma)
  const body = dataUrl.slice(comma + 1)
  if (/;base64/i.test(meta)) {
    const binary = atob(body)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return bytes.buffer
  }
  const text = decodeURIComponent(body)
  return new TextEncoder().encode(text).buffer
}

/** blob:/data: → ArrayBuffer；xhr 对 blob 比 fetch 更稳 */
async function readLocalUrlAsArrayBuffer(url: string): Promise<ArrayBuffer> {
  if (url.startsWith('data:')) {
    return dataUrlToArrayBuffer(url)
  }
  if (!url.startsWith('blob:')) {
    throw new Error(t('errors.notLocalPath'))
  }
  return await new Promise<ArrayBuffer>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', url, true)
    xhr.responseType = 'arraybuffer'
    xhr.onload = () => {
      if (xhr.status === 0 || (xhr.status >= 200 && xhr.status < 300)) {
        resolve(xhr.response as ArrayBuffer)
      } else {
        reject(new Error(t('errors.readFileFailed', { status: String(xhr.status) })))
      }
    }
    xhr.onerror = () => reject(new Error(t('errors.readLocalFileFailed')))
    xhr.send()
  })
}

async function toArrayBuffer(
  data: ArrayBuffer | Uint8Array | Blob
): Promise<ArrayBuffer> {
  if (data instanceof ArrayBuffer) return data
  if (data instanceof Uint8Array) {
    return data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) as ArrayBuffer
  }
  return await data.arrayBuffer()
}

/**
 * 按「文件管理」设置下载/保存文件。
 * - Electron：遵守下载目录 + 保存方式（询问 / 自动）
 * - Web：回退为浏览器默认下载
 */
export async function downloadFileWithSettings(
  url: string,
  fileName: string,
  options: DownloadOptions = {}
): Promise<DownloadResult> {
  if (!url && !options.data) {
    return { ok: false, message: t('errors.missingFileUrl') }
  }

  const settings = useAppSettingsStore()
  const directory = (settings.downloadPath || '').trim() || undefined
  // 「打开文件」时跳过另存为弹窗，直接写入下载目录并打开
  const askEveryTime = options.openAfter ? false : !!settings.downloadAskEveryTime
  const name = (fileName || 'download').trim() || 'download'

  const api = window.electronAPI?.downloadFile
  if (api) {
    try {
      // 1) 调用方已提供二进制（裁剪/烘焙结果）
      if (options.data) {
        const data = await toArrayBuffer(options.data)
        return await api({
          data,
          fileName: name,
          directory,
          askEveryTime,
          openAfter: options.openAfter
        })
      }

      // 2) blob:/data: 在渲染进程读入再交给主进程（不让主进程 fetch blob）
      if (url.startsWith('blob:') || url.startsWith('data:')) {
        const data = await readLocalUrlAsArrayBuffer(url)
        return await api({
          data,
          fileName: name,
          directory,
          askEveryTime,
          openAfter: options.openAfter
        })
      }

      // 3) http(s) 先走主进程；失败则渲染进程拉取后回传（兼容 MinIO 预签名）
      if (/^https?:\/\//i.test(url)) {
        const primary = await api({
          url,
          fileName: name,
          directory,
          askEveryTime,
          openAfter: options.openAfter
        })
        if (primary.ok || primary.canceled) return primary

        try {
          const res = await fetch(url)
          if (!res.ok) {
            return {
              ok: false,
              message: primary.message || t('errors.downloadFailedWithStatus', { status: String(res.status) })
            }
          }
          const data = await res.arrayBuffer()
          return await api({
            data,
            fileName: name,
            directory,
            askEveryTime,
            openAfter: options.openAfter
          })
        } catch {
          return { ok: false, message: primary.message || t('errors.downloadFailed') }
        }
      }

      return { ok: false, message: t('errors.unsupportedDownloadUrl') }
    } catch (e) {
      return { ok: false, message: e instanceof Error ? e.message : t('errors.downloadFailed') }
    }
  }

  // Web：无法控制目录；「打开」优先新窗口，否则触发下载
  try {
    if (options.data) {
      const buf = await toArrayBuffer(options.data)
      const blob = new Blob([buf])
      const objectUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = objectUrl
      a.download = name
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(objectUrl)
      return { ok: true }
    }
    if (options.openAfter && /^https?:\/\//i.test(url)) {
      window.open(url, '_blank', 'noopener,noreferrer')
      return { ok: true }
    }
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
    return { ok: false, message: e instanceof Error ? e.message : t('errors.downloadFailed') }
  }
}
