/**
 * 作者：yangleduo
 */
/**
 * 打开官网「文档」页（docs.html）。演示站走同源 /site。
 */
import { getLocale } from '../i18n'
import { LEGAL_PAGE_BASE_URL } from '../config/legalPage'
import { demoWebsiteBaseUrl } from './demoSitePages'

export async function openHelpPageInBrowser(): Promise<void> {
  const root = demoWebsiteBaseUrl() || LEGAL_PAGE_BASE_URL
  const url = new URL('docs.html', root.endsWith('/') ? root : `${root}/`)
  url.searchParams.set('lang', getLocale() === 'en-US' ? 'en' : 'zh')
  const href = url.href
  if (window.electronAPI?.openExternal) {
    const ok = await window.electronAPI.openExternal(href)
    if (!ok) window.open(href, '_blank', 'noopener,noreferrer')
    return
  }
  window.open(href, '_blank', 'noopener,noreferrer')
}
