<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴独立窗口全屏视图。
 */
import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useLinkMateStore } from '../stores/linkmate'
import { useOverlayStore } from '../stores/overlay'
import { useChatModalsStore } from '../stores/chatModals'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import LinkMateSidePanel from '../components/LinkMateSidePanel.vue'
import WindowCaptionButtons from '../components/WindowCaptionButtons.vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const route = useRoute()
const appStore = useAppStore()
const linkMate = useLinkMateStore()
const overlayStore = useOverlayStore()
const chatModalsStore = useChatModalsStore()

onMounted(async () => {
  if (!appStore.isLoggedIn) {
    await appStore.tryAutoLogin()
  }
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  overlayStore.close()
  chatModalsStore.closeAllModals()

  const sessionId = String(route.params.sessionId ?? '').trim()
  await linkMate.loadStatus()
  if (!linkMate.enabled) return

  await linkMate.loadSessions()
  if (sessionId) {
    await linkMate.selectSession(sessionId)
  } else {
    await linkMate.ensurePanelReady()
  }
  linkMate.openPanel()
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
      <div class="header-spacer" aria-hidden="true" />
      <h1 class="header-title">{{ t('nav.linkmate') }}</h1>
      <div class="header-right">
        <WindowCaptionButtons show-pin />
      </div>
    </header>
    <div class="standalone-content linkmate-standalone-content">
      <LinkMateSidePanel layout="page" standalone />
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
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  flex-shrink: 0;
  min-height: 40px;
  width: 100%;
  box-sizing: border-box;
  padding: 0 0 0 var(--lx-space-xl);
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  -webkit-app-region: drag;
}

.header-spacer {
  min-width: 138px;
  pointer-events: none;
}

.header-title {
  flex: 1;
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
  -webkit-app-region: no-drag;
}

.standalone-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.linkmate-standalone-content :deep(.linkmate-side--page) {
  width: 100%;
  height: 100%;
  max-width: none;
}
</style>
