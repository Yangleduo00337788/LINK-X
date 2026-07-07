<script setup lang="ts">
import { NMessageProvider, NDialogProvider, NConfigProvider, GlobalThemeOverrides } from 'naive-ui'
import App from './App.vue'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    borderRadius: '9px',
    borderRadiusSmall: '9px',
    primaryColor: '#12b7f5',
    primaryColorHover: '#39c2f6',
    primaryColorPressed: '#12b7f5',
  }
}
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides" style="width: 100%; height: 100%">
    <n-message-provider>
      <n-dialog-provider>
        <App />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>