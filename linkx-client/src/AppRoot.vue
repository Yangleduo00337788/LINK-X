<script setup lang="ts">
/**
 * 应用根容器组件。
 * <p>
 * 包裹 Naive UI 全局配置（主题、消息、对话框），
 * 渲染主应用，并在登录且锁屏时挂载锁屏层。
 * </p>
 */
// Vue 计算属性、侦听器与挂载钩子
import { computed, watch, onMounted, onBeforeUnmount } from 'vue'
import {
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NConfigProvider,
  darkTheme,
  zhCN,
  dateZhCN,
  enUS,
  dateEnUS,
  type GlobalThemeOverrides
} from 'naive-ui'
import App from './App.vue'
import LockScreen from './components/LockScreen.vue'
import InAppToastBridge from './components/InAppToastBridge.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from './stores/app'
import { useAppSettingsStore } from './stores/appSettings'
import { naiveThemeColors } from './theme/vars'
import { applyDocumentTheme, notifyElectronTheme } from './utils/themeSync'
import { applyAccentColor } from './utils/accentColor'
import { setLocale, localeRef } from './i18n'

// 获取应用 Store 实例
const appStore = useAppStore()
// 解构主题、登录状态、锁屏状态的响应式引用
const { theme, isLoggedIn, isLocked } = storeToRefs(appStore)

// 根据当前主题选择 Naive UI 暗色主题或 null（跟随系统亮色）
const naiveTheme = computed(() => (theme.value === 'dark' ? darkTheme : null))

const naiveLocale = computed(() => (localeRef.value === 'en-US' ? enUS : zhCN))
const naiveDateLocale = computed(() => (localeRef.value === 'en-US' ? dateEnUS : dateZhCN))

// 计算 Naive UI 主题覆盖配置，统一圆角、主色、背景与文字色
const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const isDark = theme.value === 'dark' // 是否为暗色模式
  return {
    common: {
      borderRadius: naiveThemeColors.borderRadius, // 全局圆角
      borderRadiusSmall: naiveThemeColors.borderRadius, // 小圆角
      primaryColor: naiveThemeColors.primaryColor, // 主色
      primaryColorHover: naiveThemeColors.primaryColorHover, // 主色悬停
      primaryColorPressed: naiveThemeColors.primaryColorPressed, // 主色按下
      errorColor: naiveThemeColors.errorColor, // 错误色（影响 n-result 等组件）
      errorColorHover: naiveThemeColors.errorColorHover, // 错误色悬停
      errorColorPressed: naiveThemeColors.errorColorPressed, // 错误色按下
      bodyColor: isDark ? '#1a1a1a' : '#f5f5f5', // 页面背景色
      cardColor: isDark ? '#262626' : '#ffffff', // 卡片背景色
      modalColor: isDark ? '#262626' : '#ffffff', // 弹窗背景色
      popoverColor: isDark ? '#262626' : '#ffffff', // 气泡背景色
      textColor1: isDark ? '#e5e5e5' : '#1f2329', // 主文字色
      textColor2: isDark ? '#a3a3a3' : '#8f959e', // 次文字色
      dividerColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.06)' // 分割线色
    }
  }
})

// 同步 HTML 文档主题属性与 Electron 窗口主题
function syncHtmlTheme() {
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
}

let unsubShortcutLock: (() => void) | null = null

onMounted(() => {
  syncHtmlTheme()
  const settings = useAppSettingsStore()
  setLocale(settings.language || 'zh-CN')
  void settings.syncDesktopPrefs()
  applyAccentColor(settings.accentColor || 'cyan')
  if (settings.shortcutToggleWindow || settings.shortcutLock) {
    void window.electronAPI?.setShortcuts?.({
      toggleWindow: settings.shortcutToggleWindow,
      lock: settings.shortcutLock
    })
  }
  unsubShortcutLock = window.electronAPI?.onShortcutLock?.(() => {
    if (appStore.isLoggedIn) {
      appStore.lock()
    }
  }) ?? null
})

onBeforeUnmount(() => {
  if (unsubShortcutLock) unsubShortcutLock()
})
watch(theme, syncHtmlTheme)
</script>

<template>
  <!-- Naive UI 全局配置：主题与样式覆盖 -->
  <n-config-provider
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
    style="width: 100%; height: 100%"
  >
    <!-- 全局消息提示 Provider -->
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider placement="top-right" :max="4">
          <InAppToastBridge />
          <App />
          <Teleport to="body">
            <LockScreen v-if="isLoggedIn && isLocked" />
          </Teleport>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
