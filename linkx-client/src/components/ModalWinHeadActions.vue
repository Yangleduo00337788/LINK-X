<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 弹窗标题栏右侧：与主界面相同的窗口置顶 + 窗控风格关闭。
 */
import WindowPinButton from './WindowPinButton.vue'
import { WIN_CAPTION_GLYPH, isWindowsElectron } from '../utils/windowCaptionGlyphs'
import { useI18n } from '../i18n'

withDefaults(
  defineProps<{
    showClose?: boolean
    closeDisabled?: boolean
  }>(),
  {
    showClose: true,
    closeDisabled: false
  }
)

defineEmits<{
  close: []
}>()

const { t } = useI18n()
const isElectron = !!window.electronAPI?.isElectron
const useFluentGlyph = isWindowsElectron()
</script>

<template>
  <div class="modal-win-head-actions">
    <WindowPinButton v-if="isElectron" />
    <button
      v-if="showClose"
      type="button"
      class="lx-win-caption-btn lx-win-caption-btn--close"
      :class="{ 'lx-win-caption-btn--fluent': useFluentGlyph }"
      :title="t('common.close')"
      :disabled="closeDisabled"
      @click="$emit('close')"
    >
      <span v-if="useFluentGlyph" class="lx-win-caption-glyph" aria-hidden="true">{{
        WIN_CAPTION_GLYPH.close
      }}</span>
      <svg v-else viewBox="0 0 10 10" aria-hidden="true">
        <path d="M2 2l6 6M8 2L2 8" stroke="currentColor" stroke-width="1.2" fill="none" stroke-linecap="round" />
      </svg>
    </button>
    <slot />
  </div>
</template>
