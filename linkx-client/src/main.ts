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
// 主题同步工具：写入 data-theme、跨窗口同步
import { applyDocumentTheme, initCrossWindowThemeSync } from './utils/themeSync'
// UnoCSS 原子化样式入口
import 'uno.css'
// 全局 CSS 变量与设计 Token
import './assets/styles.css'

// 创建 Pinia 实例
const pinia = createPinia()
// 注册持久化插件，使配置了 persist 的 store 自动落盘
pinia.use(piniaPluginPersistedstate)

// 以 AppRoot 为根创建 Vue 应用
const app = createApp(AppRoot)
// 挂载 Pinia
app.use(pinia)
// 挂载路由
app.use(router)

// 应用启动时读取持久化的主题并同步到 DOM
const appStore = useAppStore()
applyDocumentTheme(appStore.theme)

// 兼容历史脏持久化：曾误把 isLoggedIn/isOffline 整包写入 localStorage。
// 启动时作废脏登录态；自动登录改由登录页首帧绘制后再触发，避免「看不见 loading 就进主界面」。
appStore.isOffline = false
appStore.isLoggedIn = false

// 监听 localStorage 变化，实现多窗口（主窗口 / 友链 / 笔记）主题联动
initCrossWindowThemeSync(theme => {
  // 若其他窗口修改了主题，当前窗口跟随更新
  if (appStore.theme !== theme) {
    appStore.$patch({ theme }) // 局部更新 store 中的 theme 字段
    applyDocumentTheme(theme)  // 同步 HTML data-theme 属性
  }
})

// 路由切换前重新应用主题，防止个别页面样式漂移
router.beforeEach(() => {
  applyDocumentTheme(useAppStore().theme)
})

// 挂载到 index.html 中的 #app 节点
app.mount('#app')
