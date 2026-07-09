<script setup lang="ts">
import { onMounted, watch, nextTick } from 'vue'
import AppShell from '../components/AppShell.vue'
import LoginView from '../components/LoginView.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { applyDocumentTheme } from '../utils/themeSync'

const appStore = useAppStore()
const { isLoggedIn } = storeToRefs(appStore)

async function syncWindowMode(loggedIn: boolean) {
  if (!loggedIn) {
    await nextTick()
    await new Promise<void>(resolve => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    })
  }
  await window.electronAPI?.setWindowMode?.(loggedIn ? 'main' : 'login')
}

onMounted(() => {
  appStore.tryAutoLogin()
  applyDocumentTheme(appStore.theme)
})

watch(isLoggedIn, syncWindowMode, { immediate: true, flush: 'post' })
</script>

<template>
  <transition name="fade-slide" mode="out-in">
    <AppShell v-if="isLoggedIn" />
    <LoginView v-else />
  </transition>
</template>

<style scoped>
:deep(.app-shell) {
  width: 100%;
  height: 100%;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
