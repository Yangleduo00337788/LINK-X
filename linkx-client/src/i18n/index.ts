/**
 * 轻量 i18n：设置页与主导航等 UI 文案切换。
 * 不引入 vue-i18n 依赖，避免额外打包体积。
 */
import { computed, ref } from 'vue'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocale = 'zh-CN' | 'en-US'

type MessageTree = typeof zhCN
type NestedKeyOf<T, P extends string = ''> = T extends string
  ? P
  : {
      [K in keyof T & string]: NestedKeyOf<T[K], P extends '' ? K : `${P}.${K}`>
    }[keyof T & string]

export type MessageKey = NestedKeyOf<MessageTree>

const catalogs: Record<AppLocale, MessageTree> = {
  'zh-CN': zhCN,
  // 结构与 zh-CN 对齐，字面量类型不同故断言
  'en-US': enUS as unknown as MessageTree
}

const localeRef = ref<AppLocale>('zh-CN')

function resolve(path: string, tree: MessageTree): string | undefined {
  const parts = path.split('.')
  let cur: unknown = tree
  for (const p of parts) {
    if (cur == null || typeof cur !== 'object') return undefined
    cur = (cur as Record<string, unknown>)[p]
  }
  return typeof cur === 'string' ? cur : undefined
}

export function t(key: MessageKey | string, params?: Record<string, string | number>): string {
  const catalog = catalogs[localeRef.value] || catalogs['zh-CN']
  let text = resolve(key, catalog) ?? resolve(key, catalogs['zh-CN']) ?? key
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v))
    }
  }
  return text
}

export function getLocale(): AppLocale {
  return localeRef.value
}

export function setLocale(next: string): AppLocale {
  const normalized: AppLocale = next === 'en-US' ? 'en-US' : 'zh-CN'
  localeRef.value = normalized
  if (typeof document !== 'undefined') {
    document.documentElement.lang = normalized === 'en-US' ? 'en' : 'zh-CN'
  }
  return normalized
}

export function useI18n() {
  const locale = computed(() => localeRef.value)
  return {
    locale,
    t,
    setLocale
  }
}

export { localeRef }
