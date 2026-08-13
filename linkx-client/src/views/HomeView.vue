<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 首页视图组件。
 * <p>
 * 应用主入口页面，根据登录状态切换主壳层与登录页，
 * 并在 Electron 环境下同步窗口尺寸模式。
 * </p>
 */
import {
  onMounted,
  onUnmounted,
  watch,
  nextTick,
  ref,
  shallowRef,
  type Component
} from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { applyDocumentTheme } from '../utils/themeSync'
import { isChatSocketConnected } from '../utils/chatSocket'
// 登录页同步导入：自动登录需先画出登录窗再 loading
import LoginView from '../components/LoginView.vue'

const appStore = useAppStore()
const { isLoggedIn } = storeToRefs(appStore)

/** 主界面 chunk 预加载（登录页停留期间后台拉取，避免登录成功后白屏） */
const AppShellDef = shallowRef<Component | null>(null)
let appShellImport: Promise<Component> | null = null

function preloadAppShell(): Promise<Component> {
  if (AppShellDef.value) return Promise.resolve(AppShellDef.value)
  if (!appShellImport) {
    appShellImport = import('../components/AppShell.vue').then(m => {
      AppShellDef.value = m.default
      return m.default
    })
  }
  return appShellImport
}

/** 是否挂载主壳（登录成功后先挂壳再放大窗口） */
const showMainShell = ref(false)

function waitFrames(count = 2): Promise<void> {
  return new Promise<void>(resolve => {
    let left = count
    const step = () => {
      left -= 1
      if (left <= 0) resolve()
      else requestAnimationFrame(step)
    }
    requestAnimationFrame(step)
  })
}

async function syncWindowModeMain() {
  await window.electronAPI?.setWindowMode?.('main')
}

async function syncWindowModeLogin() {
  await nextTick()
  await waitFrames(2)
  await window.electronAPI?.setWindowMode?.('login')
}

function onMainShellMounted() {
  if (!isLoggedIn.value) return
  void waitFrames(2).then(() => syncWindowModeMain())
}

function retryWsIfNeeded() {
  if (appStore.isLoggedIn && !isChatSocketConnected()) {
    void appStore.connectChatWebSocket()
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') retryWsIfNeeded()
}

onMounted(() => {
  applyDocumentTheme(appStore.theme)
  void preloadAppShell()
  window.addEventListener('online', retryWsIfNeeded)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  window.removeEventListener('online', retryWsIfNeeded)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

watch(
  isLoggedIn,
  async loggedIn => {
    if (!loggedIn) {
      showMainShell.value = false
      await syncWindowModeLogin()
      return
    }

    await preloadAppShell()
    showMainShell.value = true
  },
  { immediate: true, flush: 'post' }
)
</script>

<template>
  <div class="home-root">
    <component
      v-if="showMainShell && AppShellDef"
      :is="AppShellDef"
      class="main-shell-layer"
      @vue:mounted="onMainShellMounted"
    />
    <Transition name="login-leave">
      <LoginView v-if="!isLoggedIn" class="login-layer" />
    </Transition>
  </div>
</template>

<style scoped>
.home-root {
  width: 100%;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  border-radius: inherit;
}

.main-shell-layer {
  width: 100%;
  height: 100%;
}

.login-layer {
  width: 100%;
  height: 100%;
}

:deep(.app-shell) {
  width: 100%;
  height: 100%;
}

.login-leave-active {
  transition:
    opacity var(--lx-duration-slow) ease,
    transform var(--lx-duration-slow) cubic-bezier(0.4, 0, 1, 1);
}

.login-leave-to {
  opacity: 0;
  transform: scale(0.97);
}

.login-enter-active {
  transition: opacity var(--lx-duration-slow) cubic-bezier(0.22, 1, 0.36, 1);
}

.login-enter-from {
  opacity: 0;
}
</style>
