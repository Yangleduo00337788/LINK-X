import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocale = 'zh-CN' | 'en-US'
export type AppTheme = 'dark' | 'light'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export function setI18nLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
}

export function tGlobal(key: string, params?: Record<string, unknown>) {
  return i18n.global.t(key, params || {})
}

export default i18n
