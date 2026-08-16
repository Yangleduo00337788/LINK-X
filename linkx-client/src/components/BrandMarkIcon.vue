<!-- 作者：yangleduo -->
<script setup lang="ts">
import logoMark from '../assets/logo-mark-transparent.png'

withDefaults(
  defineProps<{
    size?: number
    /** 未选会话主区水印：浅灰银色；其它位置保持默认彩色 */
    tone?: 'default' | 'silver'
  }>(),
  {
    size: 38,
    tone: 'default'
  }
)
</script>

<template>
  <span
    class="brand-mark-wrap"
    :class="{ 'is-silver': tone === 'silver' }"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <img class="brand-mark" :src="logoMark" alt="" draggable="false" />
  </span>
</template>

<style scoped>
.brand-mark-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: var(--lx-radius-2xs);
  background: transparent;
}

.brand-mark {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  filter: saturate(1.1) contrast(1.06);
}

.brand-mark-wrap.is-silver .brand-mark {
  filter: grayscale(1) brightness(1.18) contrast(0.88);
}

:global([data-theme='dark']) .brand-mark-wrap:not(.is-silver) {
  border-radius: var(--lx-radius-xs);
  background: rgba(255, 255, 255, 0.06);
}

:global([data-theme='dark']) .brand-mark {
  filter: saturate(1.12) brightness(1.08);
}

:global([data-theme='dark']) .brand-mark-wrap.is-silver .brand-mark {
  filter: grayscale(1) brightness(1.28) contrast(0.9);
}
</style>
