/**
 * 作者：yangleduo
 */
export type LegalDocKind = 'service' | 'privacy'

/** LinkX 官网根地址（Cloudflare Pages 自定义域） */
export const DEFAULT_OFFICIAL_WEBSITE_URL = 'https://mars-studio.asia'

/** 线上法律文档默认根地址（与官网同域） */
export const DEFAULT_LEGAL_PAGE_BASE_URL = DEFAULT_OFFICIAL_WEBSITE_URL

export function resolveLegalPageBaseUrl(envValue?: string): string {
  const value = (envValue || '').trim()
  return value || DEFAULT_LEGAL_PAGE_BASE_URL
}

export function buildLegalPageUrl(
  kind: LegalDocKind,
  locale = 'zh-CN',
  baseUrl?: string
): string {
  const remoteBase = resolveLegalPageBaseUrl(baseUrl)
  const file = kind === 'service' ? 'legal/service.html' : 'legal/privacy.html'
  const url = new URL(file, remoteBase.endsWith('/') ? remoteBase : `${remoteBase}/`)
  url.searchParams.set('lang', locale === 'en-US' ? 'en-US' : 'zh-CN')
  return url.href
}
