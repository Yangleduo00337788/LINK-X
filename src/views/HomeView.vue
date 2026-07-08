<script setup lang="ts">
import { onMounted } from 'vue'
import AppShell from '../components/AppShell.vue'
import LoginView from '../components/LoginView.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'

const appStore = useAppStore()
const { isLoggedIn } = storeToRefs(appStore)

onMounted(() => {
  appStore.tryAutoLogin()
  document.documentElement.setAttribute('data-theme', appStore.theme)
})
</script>

<template>
  <AppShell v-if="isLoggedIn" />
  <LoginView v-else />
</template>

<style scoped>
:deep(.app-shell) {
  width: 100%;
  height: 100%;
}
</style>
