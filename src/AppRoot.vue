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
import { storeToRefs } from 'pinia'
import { useAppStore } from './stores/app'
import { naiveThemeColors } from './theme/vars'
import { applyDocumentTheme, notifyElectronTheme } from './utils/themeSync'

const appStore = useAppStore()
const { theme } = storeToRefs(appStore)

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
      bodyColor: isDark ? '#2c2c2c' : '#ffffff',
      cardColor: isDark ? '#2c2c2c' : '#ffffff',
      modalColor: isDark ? '#2c2c2c' : '#ffffff',
      popoverColor: isDark ? '#2c2c2c' : '#ffffff',
      textColor1: isDark ? '#e0e0e0' : '#333333',
      textColor2: isDark ? '#aaaaaa' : '#666666',
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
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
