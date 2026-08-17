<!-- 作者：yangleduo -->
<script setup lang="ts">
import { PROJECT_LOGO_URL } from '../utils/projectLogo'

withDefaults(
  defineProps<{
    size?: number
  }>(),
  {
    size: 38
  }
)
</script>

<template>
  <span
    class="brand-mark-wrap"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <img class="brand-mark" :src="PROJECT_LOGO_URL" alt="" draggable="false" />
  </span>
</template>

<style scoped>
.brand-mark-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: var(--lx-radius-xs);
  background: transparent;
}

.brand-mark {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
}
</style>
