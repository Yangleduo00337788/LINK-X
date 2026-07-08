<script setup lang="ts">
import { NMessageProvider, NDialogProvider, NConfigProvider, GlobalThemeOverrides } from 'naive-ui'
import App from './App.vue'
import { naiveThemeColors } from './theme/vars'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    borderRadius: naiveThemeColors.borderRadius,
    borderRadiusSmall: naiveThemeColors.borderRadius,
    primaryColor: naiveThemeColors.primaryColor,
    primaryColorHover: naiveThemeColors.primaryColorHover,
    primaryColorPressed: naiveThemeColors.primaryColorPressed,
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