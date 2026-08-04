<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { buildThemeOverrides } from '@/theme/buildOverrides'
import StepUpModal from '@/components/StepUpModal.vue'

const prefs = usePreferencesStore()
useI18n()
const { theme, locale, primaryColor, roundedCorners } = storeToRefs(prefs)

const naiveTheme = computed(() => (theme.value === 'dark' ? darkTheme : null))
const themeOverrides = computed(() =>
  buildThemeOverrides(theme.value, primaryColor.value, roundedCorners.value)
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
