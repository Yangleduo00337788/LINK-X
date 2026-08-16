<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 首页视图组件。
 * <p>
 * 登录成功：先关闭登录页 → 放大窗口 → 再挂载主界面（不叠在一起）。
 * </p>
 */
import {
  onMounted,
  onUnmounted,
  watch,
  nextTick,
  ref,
  shallowRef,
  computed,
  provide,
  type Component,
  type ShallowRef
} from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { applyDocumentTheme } from '../utils/themeSync'
import { isChatSocketConnected } from '../utils/chatSocket'
import LoginView from '../components/LoginView.vue'
import { preloadAppShellComponent, preloadClientResources } from '../utils/preloadClientResources'
import { syncDesktopChromeMode } from '../utils/electronChrome'

const AWAITING_MAIN_SHELL_KEY = 'lx-awaiting-main-shell'

const appStore = useAppStore()
const { isLoggedIn } = storeToRefs(appStore)

/** 主界面 chunk 预加载（登录页停留期间后台拉取） */
const AppShellDef = shallowRef(null) as ShallowRef<Component | null>

function preloadAppShell(): Promise<Component> {
  if (AppShellDef.value) return Promise.resolve(AppShellDef.value)
  return preloadAppShellComponent().then(m => {
    AppShellDef.value = m.default as Component
    return m.default as Component
  })
}

const initialAwaitingMain =
  typeof sessionStorage !== 'undefined' &&
  sessionStorage.getItem(AWAITING_MAIN_SHELL_KEY) === '1'

const showLoginPage = ref(!initialAwaitingMain)
const showMainShell = ref(false)
/** 登录页已关、主界面未出（窗口放大等过渡） */
const bridgingToMain = computed(
  () => isLoggedIn.value && !showLoginPage.value && !showMainShell.value
)

provide(
  'loginShellReady',
  computed(() => showMainShell.value)
)

function waitMs(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function syncWindowModeMain() {
  syncDesktopChromeMode(true)
  await window.electronAPI?.setWindowMode?.('main')
}

async function syncWindowModeLogin() {
  syncDesktopChromeMode(false)
  await nextTick()
  await window.electronAPI?.setWindowMode?.('login')
}

let loginRevealGen = 0
let pendingLoginReveal = false
let revealingMain = false

async function revealMainAfterLoginClosed() {
  if (revealingMain || !pendingLoginReveal) return
  const gen = loginRevealGen
  if (!isLoggedIn.value || gen !== loginRevealGen) return

  revealingMain = true
  pendingLoginReveal = false
  try {
    sessionStorage.setItem(AWAITING_MAIN_SHELL_KEY, '1')
    await syncWindowModeMain()
    if (sessionStorage.getItem(AWAITING_MAIN_SHELL_KEY)) {
      sessionStorage.removeItem(AWAITING_MAIN_SHELL_KEY)
      if (!isLoggedIn.value || gen !== loginRevealGen) return
      showMainShell.value = true
      await nextTick()
      void appStore.connectChatWebSocket()
    }
  } finally {
    revealingMain = false
  }
}

function onLoginPageLeft() {
  void revealMainAfterLoginClosed()
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
  syncDesktopChromeMode(isLoggedIn.value)
  void preloadClientResources()
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
  async (loggedIn, previous) => {
    if (!loggedIn) {
      loginRevealGen += 1
      pendingLoginReveal = false
      showMainShell.value = false
      if (previous === true) {
        showLoginPage.value = false
        await syncWindowModeLogin()
        showLoginPage.value = true
      } else {
        syncDesktopChromeMode(false)
        showLoginPage.value = true
      }
      return
    }

    loginRevealGen += 1
    const gen = loginRevealGen

    await preloadAppShell()
    if (!isLoggedIn.value || gen !== loginRevealGen) return

    if (sessionStorage.getItem(AWAITING_MAIN_SHELL_KEY)) {
      sessionStorage.removeItem(AWAITING_MAIN_SHELL_KEY)
      showLoginPage.value = false
      syncDesktopChromeMode(true)
      await syncWindowModeMain()
      showMainShell.value = true
      void appStore.connectChatWebSocket()
      return
    }

    showMainShell.value = false
    pendingLoginReveal = true
    showLoginPage.value = false

    // 登录页未挂载时（极端情况）直接进主界面
    await nextTick()
    if (pendingLoginReveal && !showLoginPage.value) {
      await waitMs(200)
      if (pendingLoginReveal) void revealMainAfterLoginClosed()
    }
  },
  { immediate: true, flush: 'post' }
)
</script>

<template>
  <div class="home-root" :class="{ 'home-root--bridge': bridgingToMain }">
    <Transition name="main-enter">
      <component
        v-if="showMainShell && AppShellDef"
        :is="AppShellDef"
        class="main-shell-layer"
      />
    </Transition>

    <Transition name="login-leave" @after-leave="onLoginPageLeft">
      <LoginView v-if="showLoginPage" class="login-layer" />
    </Transition>
  </div>
</template>

<style scoped>
.home-root {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  border-radius: inherit;
  background: var(--lx-login-bg-gradient);
}

.home-root--bridge {
  background: var(--lx-bg-window);
}

.main-shell-layer {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
}

.login-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
}

:deep(.app-shell) {
  width: 100%;
  height: 100%;
}

.login-leave-active {
  transition: opacity 180ms ease;
}

.login-leave-to {
  opacity: 0;
}

.main-enter-active {
  transition: opacity 220ms ease;
}

.main-enter-from {
  opacity: 0;
}
</style>
