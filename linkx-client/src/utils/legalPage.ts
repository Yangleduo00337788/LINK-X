/**
 * 作者：yangleduo
 */
/**
 * 在系统默认浏览器中打开线上法律文档页（Cloudflare Pages）。
 */
import { getLocale } from '../i18n'
import { buildLegalPageUrl, LEGAL_PAGE_BASE_URL, type LegalDocKind } from '../config/legalPage'

export type { LegalDocKind }

export async function openLegalPageInBrowser(kind: LegalDocKind): Promise<void> {
  const url = buildLegalPageUrl(kind, getLocale(), LEGAL_PAGE_BASE_URL)
  if (window.electronAPI?.openExternal) {
    const ok = await window.electronAPI.openExternal(url)
    if (!ok) window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
