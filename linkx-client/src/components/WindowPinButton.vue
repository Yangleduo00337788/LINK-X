<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 窗口置顶按钮：与自绘窗控同列，统一样式与交互。
 * Win32 使用 Segoe Fluent Icons（与系统 titleBarOverlay 窗控同源字模）。
 */
import { ref, computed, onMounted } from 'vue'
import PinIcon from './icons/PinIcon.vue'
import { WIN_CAPTION_GLYPH, isWindowsElectron } from '../utils/windowCaptionGlyphs'
import { useI18n } from '../i18n'

const { t } = useI18n()
const isPinned = ref(false)
const useFluentGlyph = isWindowsElectron()

const pinTitle = computed(() => (isPinned.value ? t('shell.unpin') : t('shell.pin')))
const pinGlyph = computed(() => (isPinned.value ? WIN_CAPTION_GLYPH.pinned : WIN_CAPTION_GLYPH.pin))

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
    <span v-if="useFluentGlyph" class="lx-win-caption-glyph" aria-hidden="true">{{ pinGlyph }}</span>
    <PinIcon v-else caption :filled="isPinned" />
  </button>
</template>
