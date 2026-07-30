import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { AppLocale, AppTheme } from '@/i18n'
import { setI18nLocale } from '@/i18n'
import { setDiscreteTheme } from '@/api/request'

function applyDom(locale: AppLocale, theme: AppTheme) {
  document.documentElement.lang = locale === 'zh-CN' ? 'zh-CN' : 'en'
  document.documentElement.setAttribute('data-theme', theme)
}

function clampOpacity(v: number) {
  if (Number.isNaN(v)) return 0.12
  return Math.min(0.5, Math.max(0.02, v))
}

export const usePreferencesStore = defineStore(
  'preferences',
  () => {
    const locale = ref<AppLocale>('zh-CN')
    const theme = ref<AppTheme>('dark')
    const watermarkEnabled = ref(true)
    const watermarkFullscreen = ref(true)
    /** Multi-line watermark text; empty → use default i18n lines */
    const watermarkLines = ref<string[]>([])
    /** 0.02 ~ 0.5 */
    const watermarkOpacity = ref(0.12)

    const isDark = computed(() => theme.value === 'dark')

    const watermarkFontColor = computed(() => {
      const a = clampOpacity(watermarkOpacity.value)
      return `rgba(128, 128, 128, ${a})`
    })

    function setLocale(next: AppLocale) {
      locale.value = next
      setI18nLocale(next)
      applyDom(next, theme.value)
    }

    function setTheme(next: AppTheme) {
      theme.value = next
      setDiscreteTheme(next)
      applyDom(locale.value, next)
    }

    function toggleTheme() {
      setTheme(theme.value === 'dark' ? 'light' : 'dark')
    }

    function toggleLocale() {
      setLocale(locale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
    }

    function setWatermarkEnabled(next: boolean) {
      watermarkEnabled.value = next
    }

    function setWatermarkFullscreen(next: boolean) {
      watermarkFullscreen.value = next
    }

    function setWatermarkLines(lines: string[]) {
      watermarkLines.value = lines.map((l) => l.trim()).filter(Boolean)
    }

    function setWatermarkOpacity(next: number) {
      watermarkOpacity.value = clampOpacity(next)
    }

    function hydrate() {
      setI18nLocale(locale.value)
      setDiscreteTheme(theme.value)
      applyDom(locale.value, theme.value)
      watermarkOpacity.value = clampOpacity(watermarkOpacity.value)
    }

    watch(
      [locale, theme],
      ([l, t]) => {
        setI18nLocale(l)
        setDiscreteTheme(t)
        applyDom(l, t)
      },
      { immediate: false },
    )

    return {
      locale,
      theme,
      isDark,
      watermarkEnabled,
      watermarkFullscreen,
      watermarkLines,
      watermarkOpacity,
      watermarkFontColor,
      setLocale,
      setTheme,
      toggleTheme,
      toggleLocale,
      setWatermarkEnabled,
      setWatermarkFullscreen,
      setWatermarkLines,
      setWatermarkOpacity,
      hydrate,
    }
  },
  {
    persist: {
      key: 'linkx-admin-preferences',
      paths: [
        'locale',
        'theme',
        'watermarkEnabled',
        'watermarkFullscreen',
        'watermarkLines',
        'watermarkOpacity',
      ],
    },
  },
)
