<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NLoadingBarProvider,
  darkTheme,
  dateZhCN,
  dateEnUS,
  zhCN,
  enUS,
} from 'naive-ui'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'
import { darkThemeOverrides, lightThemeOverrides } from '@/theme/overrides'
import StepUpModal from '@/components/StepUpModal.vue'

const prefs = usePreferencesStore()
const { theme, locale } = storeToRefs(prefs)

const naiveTheme = computed(() => (theme.value === 'dark' ? darkTheme : null))
const themeOverrides = computed(() =>
  theme.value === 'dark' ? darkThemeOverrides : lightThemeOverrides
)
const naiveLocale = computed(() => (locale.value === 'zh-CN' ? zhCN : enUS))
const naiveDateLocale = computed(() => (locale.value === 'zh-CN' ? dateZhCN : dateEnUS))
</script>

<template>
  <NConfigProvider
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
  >
    <NLoadingBarProvider>
      <NDialogProvider>
        <NMessageProvider>
          <router-view />
          <StepUpModal />
        </NMessageProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
