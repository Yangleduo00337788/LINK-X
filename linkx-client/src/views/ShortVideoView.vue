<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 短视频独立窗口全屏视图。
 */
import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useAppStore } from '../stores/app'
import { useShortVideoStore } from '../stores/shortVideo'
import { useOverlayStore } from '../stores/overlay'
import { useChatModalsStore } from '../stores/chatModals'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import ShortVideoSidePanel from '../components/ShortVideoSidePanel.vue'
import WindowCaptionButtons from '../components/WindowCaptionButtons.vue'
import { syncDesktopChromeMode } from '../utils/electronChrome'
import { useI18n } from '../i18n'

const { t } = useI18n()

const appStore = useAppStore()
const shortVideo = useShortVideoStore()
const overlayStore = useOverlayStore()
const chatModalsStore = useChatModalsStore()

onMounted(async () => {
  if (!appStore.isLoggedIn) {
    await appStore.tryAutoLogin()
  }
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  syncDesktopChromeMode(appStore.isLoggedIn)
  overlayStore.close()
  chatModalsStore.closeAllModals()

  await shortVideo.ensurePanelReady()
  shortVideo.openPanel()
})

onBeforeUnmount(() => {
  overlayStore.close()
})

watch(
  () => appStore.theme,
  theme => {
    applyDocumentTheme(theme)
    notifyElectronTheme(theme)
  }
)
</script>

<template>
  <div class="standalone-shell">
    <header class="standalone-header">
      <h1 class="header-title">{{ t('nav.shortVideo') }}</h1>
      <div class="header-right">
        <WindowCaptionButtons show-pin />
      </div>
    </header>
    <div class="standalone-content">
      <ShortVideoSidePanel layout="page" standalone />
    </div>
  </div>
</template>

<style scoped>
.standalone-shell {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.standalone-header {
  position: relative;
  display: flex;
  align-items: stretch;
  flex-shrink: 0;
  height: 40px;
  min-height: 40px;
  width: 100%;
  box-sizing: border-box;
  padding: 0 0 0 var(--lx-space-xl);
  padding-right: calc(100% - env(titlebar-area-width, 100%));
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-window);
  -webkit-app-region: drag;
}

.header-title {
  position: absolute;
  inset: 0;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lx-font);
  font-weight: 500;
  color: var(--lx-text-body);
  pointer-events: none;
}

.header-right {
  display: flex;
  align-items: stretch;
  flex-shrink: 0;
  height: 100%;
  margin-left: auto;
  position: relative;
  z-index: 1;
  -webkit-app-region: no-drag;
}

.standalone-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.standalone-content :deep(.short-video-side--page) {
  width: 100%;
  height: 100%;
  max-width: none;
}
</style>
