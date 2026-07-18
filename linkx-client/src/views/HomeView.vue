<script setup lang="ts">
/**
 * 首页视图组件。
 * <p>
 * 应用主入口页面，根据登录状态切换主壳层与登录页，
 * 并在 Electron 环境下同步窗口尺寸模式。
 * </p>
 */
// Vue 挂载钩子、侦听器、异步组件与 nextTick
import { onMounted, onUnmounted, watch, nextTick, defineAsyncComponent } from 'vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 文档主题同步工具
import { applyDocumentTheme } from '../utils/themeSync'
import { isChatSocketConnected } from '../utils/chatSocket'
// 登录页同步导入：自动登录需先画出登录窗再 loading，异步会先闪空白再跳主界面
import LoginView from '../components/LoginView.vue'

// 主界面懒加载，缩短登录态首屏体积
const AppShell = defineAsyncComponent(() => import('../components/AppShell.vue'))

// 获取应用 Store 实例
const appStore = useAppStore()
// 解构登录状态的响应式引用（自动登录期间仍展示登录页，由登录按钮 loading 表达进度）
const { isLoggedIn } = storeToRefs(appStore)

// 根据登录状态同步 Electron 窗口模式（login / main）
async function syncWindowMode(loggedIn: boolean) {
  await nextTick()
  // 等一帧再改窗口尺寸，避免与主界面首屏渲染抢主线程
  await new Promise<void>(resolve => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  })
  await window.electronAPI?.setWindowMode?.(loggedIn ? 'main' : 'login')
}

function retryWsIfNeeded() {
  if (appStore.isLoggedIn && !isChatSocketConnected()) {
    void appStore.connectChatWebSocket()
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') retryWsIfNeeded()
}

// 组件挂载：应用主题（自动登录由 LoginView 首帧后触发）
onMounted(() => {
  applyDocumentTheme(appStore.theme)
  // 后端恢复 / 从后台切回时强制再连，避免桌面端停在「永久离线」
  window.addEventListener('online', retryWsIfNeeded)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  window.removeEventListener('online', retryWsIfNeeded)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

// 登录状态变化时同步窗口模式，immediate 确保首次渲染也执行
watch(isLoggedIn, syncWindowMode, { immediate: true, flush: 'post' })
</script>

<template>
  <div class="home-root">
    <!-- 直接切换，不用 transition，避免登出时父容器高度塌陷导致白屏 -->
    <Suspense v-if="isLoggedIn">
      <AppShell />
      <template #fallback>
        <div class="auth-loading" aria-busy="true" aria-label="正在进入主界面" />
      </template>
    </Suspense>
    <LoginView v-else />
  </div>
</template>

<style scoped>
.home-root {
  width: 100%;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
}

:deep(.app-shell) {
  width: 100%;
  height: 100%;
}

.auth-loading {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel, #f5f5f5);
}
</style>
