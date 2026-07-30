<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NLoadingBarProvider,
  darkTheme,
  dateZhCN,
  zhCN,
  type GlobalThemeOverrides,
} from 'naive-ui'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#5b8def',
    primaryColorHover: '#7aa3f5',
    primaryColorPressed: '#3d6fd4',
    primaryColorSuppl: '#5b8def',
    bodyColor: '#0f1115',
    cardColor: '#171a21',
    modalColor: '#171a21',
    popoverColor: '#1c2029',
    tableColor: '#171a21',
    borderColor: '#2a2f3a',
    dividerColor: '#2a2f3a',
    textColorBase: '#e8eaed',
    textColor1: '#e8eaed',
    textColor2: '#a8b0bd',
    textColor3: '#7a8494',
    fontFamily: '"IBM Plex Sans", "Segoe UI", sans-serif',
    borderRadius: '6px',
  },
  Layout: {
    siderColor: '#12151b',
    headerColor: '#12151b',
  },
  Menu: {
    itemTextColor: '#a8b0bd',
    itemTextColorHover: '#e8eaed',
    itemTextColorActive: '#e8eaed',
    itemTextColorActiveHover: '#e8eaed',
    itemIconColor: '#7a8494',
    itemIconColorHover: '#e8eaed',
    itemIconColorActive: '#5b8def',
    itemColorActive: 'rgba(91, 141, 239, 0.12)',
    itemColorHover: 'rgba(255, 255, 255, 0.04)',
  },
}

const theme = computed(() => darkTheme)
</script>

<template>
  <NConfigProvider
    :theme="theme"
    :theme-overrides="themeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
  >
    <NLoadingBarProvider>
      <NDialogProvider>
        <NMessageProvider>
          <router-view />
        </NMessageProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
