<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AppShell from './components/AppShell.vue'
import MomentsModal from './components/MomentsModal.vue'
import NoteEditor from './components/NoteEditor.vue'
import LoginView from './components/LoginView.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from './stores/app'

const appStore = useAppStore()
const { isLoggedIn } = storeToRefs(appStore)
const isMomentsWindow = ref(false)
const isNoteEditorWindow = ref(false)

onMounted(() => {
  appStore.tryAutoLogin()
  document.documentElement.setAttribute('data-theme', appStore.theme)

  if (window.location.hash.includes('moments')) {
    isMomentsWindow.value = true
  } else if (window.location.hash.includes('note-editor')) {
    isNoteEditorWindow.value = true
  }
})
</script>

<template>
  <MomentsModal v-if="isMomentsWindow" />
  <NoteEditor v-else-if="isNoteEditorWindow" />
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
