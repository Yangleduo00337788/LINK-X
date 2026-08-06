/**
 * 在系统默认浏览器中打开法律文档静态页（public/legal/*.html）。
 */
import { getLocale } from '../i18n'

export type LegalDocKind = 'service' | 'privacy'

function resolveLegalPageUrl(kind: LegalDocKind): string {
  const file = kind === 'service' ? 'legal/service.html' : 'legal/privacy.html'
  const devBase = import.meta.env.VITE_DEV_SERVER_URL as string | undefined
  const pageBase =
    import.meta.env.DEV && devBase
      ? devBase.endsWith('/') ? devBase : `${devBase}/`
      : window.location.href.split('#')[0]
  const url = new URL(file, pageBase)
  url.searchParams.set('lang', getLocale())
  return url.href
}

export async function openLegalPageInBrowser(kind: LegalDocKind): Promise<void> {
  const url = resolveLegalPageUrl(kind)
  if (window.electronAPI?.openExternal) {
    const ok = await window.electronAPI.openExternal(url)
    if (!ok) window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
