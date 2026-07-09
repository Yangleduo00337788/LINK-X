<script setup lang="ts">
/**
 * 应用根容器组件。
 * <p>
 * 包裹 Naive UI 全局配置（主题、消息、对话框），
 * 渲染主应用、设置弹窗，并在登录且锁屏时挂载锁屏层。
 * </p>
 */
// Vue 计算属性、侦听器与挂载钩子
import { computed, watch, onMounted } from 'vue'
// Naive UI 全局 Provider 与暗色主题
import {
  NMessageProvider,
  NDialogProvider,
  NConfigProvider,
  darkTheme,
  type GlobalThemeOverrides
} from 'naive-ui'
// 路由根组件
import App from './App.vue'
// Vue 异步组件定义
import { defineAsyncComponent } from 'vue'

// 设置弹窗异步懒加载，减小首屏包体积
const SettingsModal = defineAsyncComponent(() => import('./components/SettingsModal.vue'))
// 锁屏组件同步导入
import LockScreen from './components/LockScreen.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from './stores/app'
// Naive UI 主题色常量
import { naiveThemeColors } from './theme/vars'
// 文档主题同步与 Electron 主题通知工具
import { applyDocumentTheme, notifyElectronTheme } from './utils/themeSync'

// 获取应用 Store 实例
const appStore = useAppStore()
// 解构主题、登录状态、锁屏状态的响应式引用
const { theme, isLoggedIn, isLocked } = storeToRefs(appStore)

// 根据当前主题选择 Naive UI 暗色主题或 null（跟随系统亮色）
const naiveTheme = computed(() => (theme.value === 'dark' ? darkTheme : null))

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
  applyDocumentTheme(appStore.theme) // 设置 document 上的 data-theme
  notifyElectronTheme(appStore.theme) // 通知 Electron 主进程切换原生主题
}

// 挂载时立即同步一次主题
onMounted(syncHtmlTheme)
// 主题变化时重新同步
watch(theme, syncHtmlTheme)
</script>

<template>
  <!-- Naive UI 全局配置：主题与样式覆盖 -->
  <n-config-provider
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
    style="width: 100%; height: 100%"
  >
    <!-- 全局消息提示 Provider -->
    <n-message-provider>
      <!-- 全局对话框 Provider -->
      <n-dialog-provider>
        <!-- 主应用（路由出口） -->
        <App />
        <!-- 设置弹窗（异步加载） -->
        <SettingsModal />
        <!-- 锁屏层：已登录且处于锁定状态时挂载到 body -->
        <Teleport to="body">
          <LockScreen v-if="isLoggedIn && isLocked" />
        </Teleport>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
