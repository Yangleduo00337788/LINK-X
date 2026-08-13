<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 窗口置顶按钮：与自绘窗控同列，统一样式与交互。
 */
import { ref, computed, onMounted } from 'vue'
import PinIcon from './icons/PinIcon.vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const isPinned = ref(false)

const pinTitle = computed(() => (isPinned.value ? t('shell.unpin') : t('shell.pin')))

onMounted(async () => {
  if (window.electronAPI?.isPinned) {
    isPinned.value = await window.electronAPI.isPinned()
  }
})

async function togglePin() {
  if (!window.electronAPI?.togglePin) return
  isPinned.value = await window.electronAPI.togglePin()
}
</script>

<template>
  <button
    type="button"
    class="lx-win-caption-btn lx-win-caption-btn--pin"
    :class="{ 'is-active': isPinned }"
    :title="pinTitle"
    :aria-pressed="isPinned"
    @click="togglePin"
  >
    <PinIcon :size="10" :filled="isPinned" />
  </button>
</template>
