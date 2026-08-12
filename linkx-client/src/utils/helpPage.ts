/**
 * 作者：yangleduo
 */
/**
 * 在系统默认浏览器中打开线上帮助文档（Cloudflare Pages）。
 */
import { getLocale } from '../i18n'
import { buildHelpPageUrl, HELP_PAGE_BASE_URL } from '../config/helpPage'

export async function openHelpPageInBrowser(articleId?: string): Promise<void> {
  const url = buildHelpPageUrl(getLocale(), articleId, HELP_PAGE_BASE_URL)
  if (window.electronAPI?.openExternal) {
    const ok = await window.electronAPI.openExternal(url)
    if (!ok) window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
