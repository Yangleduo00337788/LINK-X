/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { AppLocale, AppTheme } from '@/i18n'
import { setI18nLocale } from '@/i18n'
import { setDiscreteTheme } from '@/api/request'
import { hexToRgb, primaryPalette } from '@/theme/colorUtils'
import {
  DEFAULT_PRIMARY_COLOR,
  type AppearancePreset,
  type FormContainerStyle,
  type LayoutMode,
} from '@/theme/presets'

function applyDom(locale: AppLocale, theme: AppTheme) {
  document.documentElement.lang = locale === 'zh-CN' ? 'zh-CN' : 'en'
  document.documentElement.setAttribute('data-theme', theme)
}

function clampOpacity(v: number) {
  if (Number.isNaN(v)) return 0.12
  return Math.min(0.5, Math.max(0.02, v))
}

function applyVisualPreferences(input: {
  primaryColor: string
  navTheme: 'light' | 'dark'
  layoutMode: LayoutMode
  appearancePreset: AppearancePreset
  roundedCorners: boolean
  grayscaleMode: boolean
  headerThemeColor: boolean
  headerThemeFull: boolean
}) {
  const root = document.documentElement
  const pal = primaryPalette(input.primaryColor)
  const rgb = hexToRgb(input.primaryColor)
  const radius = input.roundedCorners ? '8px' : '4px'

  root.style.setProperty('--lx-accent', pal.primary)
  root.style.setProperty('--lx-oa-blue', pal.primary)
  root.style.setProperty('--lx-stat-accent', pal.primary)
  root.style.setProperty('--lx-accent-hover', pal.hover)
  root.style.setProperty('--lx-accent-pressed', pal.pressed)
  root.style.setProperty('--lx-radius', radius)
  if (rgb) {
    root.style.setProperty('--lx-primary-rgb', `${rgb.r}, ${rgb.g}, ${rgb.b}`)
    root.style.setProperty('--lx-login-grad-1', `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.22)`)
  }

  root.setAttribute('data-nav-theme', input.navTheme)
  root.setAttribute('data-layout', input.layoutMode)
  root.setAttribute('data-appearance', input.appearancePreset)
  root.setAttribute('data-header-primary', input.headerThemeColor ? 'true' : 'false')
  root.setAttribute('data-header-primary-full', input.headerThemeFull ? 'true' : 'false')
  root.setAttribute('data-grayscale', input.grayscaleMode ? 'true' : 'false')
}

export const usePreferencesStore = defineStore(
  'preferences',
  () => {
    const locale = ref<AppLocale>('zh-CN')
    const theme = ref<AppTheme>('light')
    const appearancePreset = ref<AppearancePreset>('mixed')
    const layoutMode = ref<LayoutMode>('side')
    const primaryColor = ref(DEFAULT_PRIMARY_COLOR)
    const headerThemeColor = ref(false)
    const headerThemeFull = ref(false)
    const moduleDockEnabled = ref(true)
    const breadcrumbEnabled = ref(true)
    const multiTabEnabled = ref(true)
    const siderCollapsedDefault = ref(false)
    const menuAccordion = ref(true)
    const footerEnabled = ref(false)
    const footerText = ref('')
    const roundedCorners = ref(false)
    const formStyle = ref<FormContainerStyle>('drawer')
    const grayscaleMode = ref(false)

    const watermarkEnabled = ref(true)
    const watermarkFullscreen = ref(true)
    const watermarkLines = ref<string[]>([])
    const watermarkOpacity = ref(0.12)
    const voiceNotifyEnabled = ref(true)
    const speechVoiceUri = ref('')

    const navTheme = computed<'light' | 'dark'>(() => {
      if (appearancePreset.value === 'mixed') return 'dark'
      if (appearancePreset.value === 'light') return 'light'
      return 'dark'
    })

    const isDark = computed(() => theme.value === 'dark')

    const watermarkFontColor = computed(() => {
      const a = clampOpacity(watermarkOpacity.value)
      return `rgba(128, 128, 128, ${a})`
    })

    function syncAppearanceTheme() {
      if (appearancePreset.value === 'dark') {
        theme.value = 'dark'
      } else {
        // mixed / light → 内容区浅色
        theme.value = 'light'
      }
    }

    function applyAll() {
      applyDom(locale.value, theme.value)
      setDiscreteTheme(theme.value)
      applyVisualPreferences({
        primaryColor: primaryColor.value,
        navTheme: navTheme.value,
        layoutMode: layoutMode.value,
        appearancePreset: appearancePreset.value,
        roundedCorners: roundedCorners.value,
        grayscaleMode: grayscaleMode.value,
        headerThemeColor: headerThemeColor.value,
        headerThemeFull: headerThemeFull.value,
      })
    }

    function setLocale(next: AppLocale) {
      locale.value = next
      setI18nLocale(next)
      applyDom(next, theme.value)
    }

    function setTheme(next: AppTheme) {
      theme.value = next
      if (next === 'dark') appearancePreset.value = 'dark'
      else if (appearancePreset.value === 'dark') appearancePreset.value = 'light'
      setDiscreteTheme(next)
      applyAll()
    }

    function setAppearancePreset(next: AppearancePreset) {
      appearancePreset.value = next
      syncAppearanceTheme()
      applyAll()
    }

    function setLayoutMode(next: LayoutMode) {
      layoutMode.value = next
      applyAll()
    }

    function setPrimaryColor(next: string) {
      primaryColor.value = next
      applyAll()
    }

    function setHeaderThemeColor(next: boolean) {
      headerThemeColor.value = next
      applyAll()
    }

    function setHeaderThemeFull(next: boolean) {
      headerThemeFull.value = next
      applyAll()
    }

    function setModuleDockEnabled(next: boolean) {
      moduleDockEnabled.value = next
    }

    function setBreadcrumbEnabled(next: boolean) {
      breadcrumbEnabled.value = next
    }

    function setMultiTabEnabled(next: boolean) {
      multiTabEnabled.value = next
    }

    function setSiderCollapsedDefault(next: boolean) {
      siderCollapsedDefault.value = next
    }

    function setMenuAccordion(next: boolean) {
      menuAccordion.value = next
    }

    function setFooterEnabled(next: boolean) {
      footerEnabled.value = next
    }

    function setFooterText(next: string) {
      footerText.value = next
    }

    function setRoundedCorners(next: boolean) {
      roundedCorners.value = next
      applyAll()
    }

    function setFormStyle(next: FormContainerStyle) {
      formStyle.value = next
    }

    function setGrayscaleMode(next: boolean) {
      grayscaleMode.value = next
      applyAll()
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

    function setVoiceNotifyEnabled(next: boolean) {
      voiceNotifyEnabled.value = next
    }

    function setSpeechVoiceUri(next: string) {
      speechVoiceUri.value = next
    }

    function hydrate() {
      try {
        const raw = localStorage.getItem('linkx-admin-preferences')
        if (raw) {
          const parsed = JSON.parse(raw) as {
            theme?: AppTheme
            appearancePreset?: AppearancePreset
          }
          if (!parsed.appearancePreset && parsed.theme === 'dark') {
            appearancePreset.value = 'dark'
          }
        }
      } catch {
        /* ignore */
      }
      syncAppearanceTheme()
      setI18nLocale(locale.value)
      watermarkOpacity.value = clampOpacity(watermarkOpacity.value)
      applyAll()
    }

    watch(
      [locale, theme, primaryColor, appearancePreset, layoutMode],
      () => {
        setI18nLocale(locale.value)
        applyAll()
      },
      { immediate: false }
    )

    return {
      locale,
      theme,
      appearancePreset,
      layoutMode,
      primaryColor,
      headerThemeColor,
      headerThemeFull,
      moduleDockEnabled,
      breadcrumbEnabled,
      multiTabEnabled,
      siderCollapsedDefault,
      menuAccordion,
      footerEnabled,
      footerText,
      roundedCorners,
      formStyle,
      grayscaleMode,
      navTheme,
      isDark,
      watermarkEnabled,
      watermarkFullscreen,
      watermarkLines,
      watermarkOpacity,
      watermarkFontColor,
      voiceNotifyEnabled,
      speechVoiceUri,
      setLocale,
      setTheme,
      setAppearancePreset,
      setLayoutMode,
      setPrimaryColor,
      setHeaderThemeColor,
      setHeaderThemeFull,
      setModuleDockEnabled,
      setBreadcrumbEnabled,
      setMultiTabEnabled,
      setSiderCollapsedDefault,
      setMenuAccordion,
      setFooterEnabled,
      setFooterText,
      setRoundedCorners,
      setFormStyle,
      setGrayscaleMode,
      toggleTheme,
      toggleLocale,
      setWatermarkEnabled,
      setWatermarkFullscreen,
      setWatermarkLines,
      setWatermarkOpacity,
      setVoiceNotifyEnabled,
      setSpeechVoiceUri,
      hydrate,
    }
  },
  {
    persist: {
      key: 'linkx-admin-preferences',
      paths: [
        'locale',
        'theme',
        'appearancePreset',
        'layoutMode',
        'primaryColor',
        'headerThemeColor',
        'headerThemeFull',
        'moduleDockEnabled',
        'breadcrumbEnabled',
        'multiTabEnabled',
        'siderCollapsedDefault',
        'menuAccordion',
        'footerEnabled',
        'footerText',
        'roundedCorners',
        'formStyle',
        'grayscaleMode',
        'watermarkEnabled',
        'watermarkFullscreen',
        'watermarkLines',
        'watermarkOpacity',
        'voiceNotifyEnabled',
        'speechVoiceUri',
      ],
    },
  }
)
