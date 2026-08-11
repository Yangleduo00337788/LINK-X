<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * LinkX 官方通知详情独立窗口 / 新标签页。
 */
import { onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useOverlayStore } from '../stores/overlay'
import { useChatModalsStore } from '../stores/chatModals'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import OfficialNotifyDetailPage from '../components/official/OfficialNotifyDetailPage.vue'
import WindowCaptionButtons from '../components/WindowCaptionButtons.vue'
import { ChevronBackOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { useI18n } from '../i18n'
import { LxIconButton } from '../components/ui'

const { t } = useI18n()
const router = useRouter()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const chatModalsStore = useChatModalsStore()

const showBack = computed(
  () => sessionStorage.getItem('official-detail-in-main') === '1'
)

function goBack() {
  sessionStorage.removeItem('official-detail-in-main')
  void router.push('/')
}

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
      <div class="header-left">
        <LxIconButton
          v-if="showBack"
          class="back-btn"
          :title="t('common.back')"
          @click="goBack"
        >
          <n-icon :component="ChevronBackOutline" :size="20" />
        </LxIconButton>
        <div v-else class="header-spacer" aria-hidden="true" />
      </div>
      <h1 class="header-title">{{ t('chat.officialDetail') }}</h1>
      <div class="header-right">
        <WindowCaptionButtons v-if="!showBack" />
      </div>
    </header>
    <div class="standalone-content">
      <OfficialNotifyDetailPage />
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

:global([data-theme='dark']) .standalone-shell {
  background: var(--lx-bg-window);
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
  border-bottom: 1px solid var(--lx-divider);
  background: var(--lx-bg-panel);
  -webkit-app-region: drag;
}

:global([data-theme='dark']) .standalone-header {
  background: var(--lx-bg-card);
  border-bottom-color: var(--lx-divider);
}

.header-spacer {
  width: 36px;
  pointer-events: none;
}

.header-left {
  min-width: 138px;
  display: flex;
  align-items: center;
  -webkit-app-region: no-drag;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lx-radius-sm);
  background: transparent;
  color: var(--lx-text-body);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.back-btn:hover {
  background: rgba(0, 0, 0, 0.06);
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
  overflow-y: auto;
  padding: var(--lx-space-3xl) var(--lx-space-2xl) var(--lx-space-5xl-minus);
  box-sizing: border-box;
}
</style>
