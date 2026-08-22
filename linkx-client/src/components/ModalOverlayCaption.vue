<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 全屏遮罩弹窗右上角窗口置顶（与 MainStatusBar 同源 WindowPinButton）。
 */
import WindowPinButton from './WindowPinButton.vue'

withDefaults(
  defineProps<{
    /** Teleport 到 body 等无定位父级时使用 fixed */
    fixed?: boolean
  }>(),
  {
    fixed: false
  }
)

const isElectron = !!window.electronAPI?.isElectron
</script>

<template>
  <div
    v-if="isElectron"
    class="modal-overlay-caption"
    :class="{ 'modal-overlay-caption--fixed': fixed }"
  >
    <WindowPinButton />
  </div>
</template>
