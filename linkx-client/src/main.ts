/**
 * 作者：yangleduo
 */
// 从 Vue 3 导入应用工厂函数
import { createApp } from 'vue'
// 导入 Pinia 状态管理库
import { createPinia } from 'pinia'
// Pinia 持久化插件：将 store 状态写入 localStorage
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
// Naive UI 组件按需引入，勿全量 app.use(naive) 以免拖慢启动
// 根组件：包裹主题 Provider 与全局弹窗
import AppRoot from './AppRoot.vue'
// Vue Router 路由实例
import router from './router'
// 全局应用 Store，用于读取初始主题
import { useAppStore } from './stores/app'
import { useAppSettingsStore } from './stores/appSettings'
// 主题同步工具：写入 data-theme、跨窗口同步、跟随系统
import {
  applyDocumentTheme,
  initCrossWindowThemeSync,
  resolveThemePreference
} from './utils/themeSync'
import { setLocale } from './i18n'
// UnoCSS 原子化样式入口
import 'uno.css'
// 全局 CSS 变量与设计 Token
import './assets/styles.css'
import './styles/ui-components.css'
import { debouncedSessionStorage } from './utils/debouncedStorage'
import { reportBootError } from './utils/bootSplash'
import { installGlobalWheelScrollDamping } from './utils/wheelScrollDamping'

if (typeof location !== 'undefined' && /tray-message/.test(location.hash)) {
  document.documentElement.classList.add('lx-tray-popup')
}

// 创建 Pinia 实例
const pinia = createPinia()
// 注册持久化插件，使配置了 persist 的 store 自动落盘
pinia.use(piniaPluginPersistedstate)

// 以 AppRoot 为根创建 Vue 应用
const app = createApp(AppRoot)
app.config.errorHandler = (err, _instance, info) => {
  console.error('[vue]', err, info)
  reportBootError(err instanceof Error ? err.message : String(err))
}
// 挂载 Pinia
app.use(pinia)
// 挂载路由
app.use(router)

// 启动时按外观偏好解析主题（跟随系统则读 OS，避免闪旧主题）
const appStore = useAppStore()
const settingsStore = useAppSettingsStore()
const bootTheme = resolveThemePreference(settingsStore.themeMode || 'light')
if (appStore.theme !== bootTheme) {
  appStore.theme = bootTheme
}
applyDocumentTheme(bootTheme)

// Electron：标记桌面壳层（Win32 登录窗无边框，登录后切原生边框）
if (window.electronAPI?.isElectron) {
  document.documentElement.classList.add('lx-electron')
  if (window.electronAPI.getPlatform?.() === 'windows') {
    document.documentElement.classList.add('lx-electron-win32')
  }
  const syncMaximized = (maximized: boolean) => {
    document.documentElement.classList.toggle('is-maximized', maximized)
  }
  void window.electronAPI.isMaximized?.().then(syncMaximized).catch(() => {})
  window.electronAPI.onMaximizedChange?.(syncMaximized)
}

// 从本地偏好恢复界面语言（pinia persist 已在 use 后可读）
setLocale(settingsStore.language || 'zh-CN')

appStore.isOffline = false
const postLoginEnter =
  typeof localStorage !== 'undefined' &&
  localStorage.getItem('lx-post-login-enter') === '1'
if (postLoginEnter) {
  localStorage.removeItem('lx-post-login-enter')
}
appStore.isLoggedIn = !!postLoginEnter

// 监听 localStorage 变化，实现多窗口（主窗口 / 友链 / 笔记）主题联动
initCrossWindowThemeSync(theme => {
  // 若其他窗口修改了主题，当前窗口跟随更新
  if (appStore.theme !== theme) {
    appStore.$patch({ theme }) // 局部更新 store 中的 theme 字段
    applyDocumentTheme(theme)  // 同步 HTML data-theme 属性
  }
})

// 切后台/关闭前立即同步已读游标，避免仅本地清未读、服务端未更新
if (typeof document !== 'undefined') {
  const flushReadOnHide = () => {
    if (document.visibilityState !== 'hidden') return
    debouncedSessionStorage.flushAll()
    void useAppStore().flushReportSessionRead()
  }
  document.addEventListener('visibilitychange', flushReadOnHide)
  window.addEventListener('pagehide', flushReadOnHide)
}

// 路由切换前重新应用主题，防止个别页面样式漂移
router.beforeEach(to => {
  document.documentElement.classList.toggle('lx-tray-popup', to.name === 'tray-message')
  applyDocumentTheme(useAppStore().theme)
})

// 全局降低滚轮滚动速度，便于阅读长列表
installGlobalWheelScrollDamping()

// 挂载到 index.html 中的 #app 节点
try {
  app.mount('#app')
} catch (error) {
  console.error('[boot] mount failed', error)
  reportBootError(error instanceof Error ? error.message : String(error))
}
