/**
 * 作者：yangleduo
 */
import { DEFAULT_LEGAL_PAGE_BASE_URL } from './legalPage'

/** 线上帮助文档默认根地址（Cloudflare Pages，与法律文档同域 /help 子路径） */
export const DEFAULT_HELP_PAGE_BASE_URL = `${DEFAULT_LEGAL_PAGE_BASE_URL.replace(/\/$/, '')}/help`

export function resolveHelpPageBaseUrl(envValue?: string): string {
  const value = (envValue || '').trim()
  return value || DEFAULT_HELP_PAGE_BASE_URL
}

export function buildHelpPageUrl(
  locale = 'zh-CN',
  articleId?: string,
  baseUrl?: string
): string {
  const remoteBase = resolveHelpPageBaseUrl(baseUrl)
  const url = new URL('index.html', remoteBase.endsWith('/') ? remoteBase : `${remoteBase}/`)
  url.searchParams.set('lang', locale === 'en-US' ? 'en-US' : 'zh-CN')
  if (articleId && articleId.trim()) {
    url.searchParams.set('article', articleId.trim())
  }
  return url.href
}
