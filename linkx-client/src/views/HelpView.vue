<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 帮助中心独立窗口全屏视图。
 * <p>
 * 像友链独立窗口那样，全宽沉浸式渲染，无 page-shell 包裹。
 * </p>
 */
import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useAppStore } from '../stores/app'
import { useOverlayStore } from '../stores/overlay'
import { useChatModalsStore } from '../stores/chatModals'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import HelpPage from '../components/overlay/pages/HelpPage.vue'
import WindowCaptionButtons from '../components/WindowCaptionButtons.vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const chatModalsStore = useChatModalsStore()

// 进入页面：确保关闭其他 Overlay/弹窗，独立窗口主题同步
onMounted(async () => {
  if (!appStore.isLoggedIn) {
    await appStore.tryAutoLogin()
  }
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  overlayStore.close()
  chatModalsStore.closeAllModals()
})

onBeforeUnmount(() => {
  overlayStore.close()
})

watch(() => appStore.theme, (theme) => {
  applyDocumentTheme(theme)
  notifyElectronTheme(theme)
})
</script>

<template>
  <div class="standalone-shell">
    <header class="standalone-header">
      <div class="header-spacer" aria-hidden="true" />
      <h1 class="header-title">{{ t('overlay.helpTitle') }}</h1>
      <div class="header-right">
        <WindowCaptionButtons />
      </div>
    </header>
    <div class="standalone-content">
      <HelpPage />
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
  padding: 0 0 0 14px;
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
  font-size: 14px;
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
  overflow-y: auto;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}
</style>
