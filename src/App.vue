<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AppShell from './components/AppShell.vue'
import MomentsModal from './components/MomentsModal.vue'
import LoginView from './components/LoginView.vue'
import { useAppState } from './composables/useAppState'

const { isLoggedIn } = useAppState()
const isMomentsWindow = ref(false)

onMounted(() => {
  // 根据 URL hash 判断当前是否是朋友圈独立窗口
  if (window.location.hash.includes('moments')) {
    isMomentsWindow.value = true
  }
})
</script>

<template>
  <MomentsModal v-if="isMomentsWindow" />
  <template v-else>
    <AppShell v-if="isLoggedIn" />
    <LoginView v-else />
  </template>
</template>

<style scoped>
:deep(.app-shell) {
  width: 100%;
  height: 100%;
}
</style>