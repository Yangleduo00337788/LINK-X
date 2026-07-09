<script setup lang="ts">
import { computed, watch, onMounted } from 'vue'
import {
  NMessageProvider,
  NDialogProvider,
  NConfigProvider,
  darkTheme,
  type GlobalThemeOverrides
} from 'naive-ui'
import App from './App.vue'
import { defineAsyncComponent } from 'vue'

const SettingsModal = defineAsyncComponent(() => import('./components/SettingsModal.vue'))
import LockScreen from './components/LockScreen.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from './stores/app'
import { naiveThemeColors } from './theme/vars'
import { applyDocumentTheme, notifyElectronTheme } from './utils/themeSync'

const appStore = useAppStore()
const { theme, isLoggedIn, isLocked } = storeToRefs(appStore)

const naiveTheme = computed(() => (theme.value === 'dark' ? darkTheme : null))

const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const isDark = theme.value === 'dark'
  return {
    common: {
      borderRadius: naiveThemeColors.borderRadius,
      borderRadiusSmall: naiveThemeColors.borderRadius,
      primaryColor: naiveThemeColors.primaryColor,
      primaryColorHover: naiveThemeColors.primaryColorHover,
      primaryColorPressed: naiveThemeColors.primaryColorPressed,
      bodyColor: isDark ? '#1a1a1a' : '#f5f5f5',
      cardColor: isDark ? '#262626' : '#ffffff',
      modalColor: isDark ? '#262626' : '#ffffff',
      popoverColor: isDark ? '#262626' : '#ffffff',
      textColor1: isDark ? '#e5e5e5' : '#1f2329',
      textColor2: isDark ? '#a3a3a3' : '#8f959e',
      dividerColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.06)'
    }
  }
})

function syncHtmlTheme() {
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
}

onMounted(syncHtmlTheme)
watch(theme, syncHtmlTheme)
</script>

<template>
  <n-config-provider
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
    style="width: 100%; height: 100%"
  >
    <n-message-provider>
      <n-dialog-provider>
        <App />
        <SettingsModal />
        <Teleport to="body">
          <LockScreen v-if="isLoggedIn && isLocked" />
        </Teleport>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
