import { computed, ref } from 'vue'
import { installerT, resolveInstallerLocale, type InstallerLocale } from '../shared/i18n'

const localeRef = ref<InstallerLocale>(
  resolveInstallerLocale(typeof navigator !== 'undefined' ? navigator.language : 'zh-CN')
)

export function setInstallerLocale(locale: InstallerLocale) {
  localeRef.value = locale
}

export function useInstallerI18n() {
  const locale = computed(() => localeRef.value)
  const t = (key: string, params?: Record<string, string | number>) =>
    installerT(localeRef.value, key, params)
  return { locale, t, setLocale: setInstallerLocale }
}
