<script setup lang="ts">
import { NMessageProvider, NDialogProvider } from 'naive-ui'
import App from './App.vue'
</script>

<template>
  <n-message-provider>
    <n-dialog-provider>
      <App />
    </n-dialog-provider>
  </n-message-provider>
</template>