import { ref } from 'vue'

const isSettingsModalVisible = ref(false)

export function useSettings() {
  function openSettings() {
    isSettingsModalVisible.value = true
  }

  function closeSettings() {
    isSettingsModalVisible.value = false
  }

  return {
    isSettingsModalVisible,
    openSettings,
    closeSettings
  }
}
