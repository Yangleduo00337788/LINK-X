<script setup lang="ts">
/**
 * 首页视图组件。
 * <p>
 * 应用主入口页面，根据登录状态切换主壳层与登录页，
 * 并在 Electron 环境下同步窗口尺寸模式。
 * </p>
 */
// Vue 挂载钩子、侦听器与 nextTick
import { onMounted, watch, nextTick } from 'vue'
// 主应用壳层（侧边栏 + 列布局）
import AppShell from '../components/AppShell.vue'
// 登录页
import LoginView from '../components/LoginView.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 文档主题同步工具
import { applyDocumentTheme } from '../utils/themeSync'

// 获取应用 Store 实例
const appStore = useAppStore()
// 解构登录状态的响应式引用
const { isLoggedIn, authInitializing } = storeToRefs(appStore)

// 根据登录状态同步 Electron 窗口模式（login / main）
async function syncWindowMode(loggedIn: boolean) {
  if (!loggedIn) {
    // 退出登录时等待 DOM 更新与两帧动画，确保布局切换完成后再调整窗口
    await nextTick()
    await new Promise<void>(resolve => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    })
  }
  // 调用 Electron API 切换窗口尺寸：主界面或登录页
  await window.electronAPI?.setWindowMode?.(loggedIn ? 'main' : 'login')
}

// 组件挂载：尝试自动登录并应用主题
onMounted(async () => {
  await appStore.tryAutoLogin()
  applyDocumentTheme(appStore.theme)
})

// 登录状态变化时同步窗口模式，immediate 确保首次渲染也执行
watch(isLoggedIn, syncWindowMode, { immediate: true, flush: 'post' })
</script>

<template>
  <div class="home-root">
    <!-- 自动登录恢复会话时显示加载态，避免登录页闪烁 -->
    <div v-if="authInitializing" class="auth-loading" aria-busy="true" aria-label="正在恢复登录状态" />
    <!-- 直接切换，不用 transition，避免登出时父容器高度塌陷导致白屏 -->
    <AppShell v-else-if="isLoggedIn" />
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
