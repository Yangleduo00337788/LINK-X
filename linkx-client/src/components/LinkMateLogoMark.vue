<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴 Logo：白底圆角容器 + 去黑底后的 mark 图。
 */
import { computed, onMounted, ref } from 'vue'
import { loadLinkMateLogoOnWhite } from '../utils/linkmateLogo'

const props = withDefaults(
  defineProps<{
    /** 像素尺寸，或预设 sm / hdr / msg / lg */
    size?: 'sm' | 'hdr' | 'msg' | 'lg' | number
  }>(),
  { size: 'sm' }
)

const src = ref('')

const frameStyle = computed(() => {
  if (typeof props.size === 'number') {
    return { width: `${props.size}px`, height: `${props.size}px` }
  }
  return undefined
})

const frameClass = computed(() =>
  typeof props.size === 'string' ? `linkmate-mark--${props.size}` : 'linkmate-mark--custom'
)

onMounted(async () => {
  src.value = await loadLinkMateLogoOnWhite()
})
</script>

<template>
  <span class="linkmate-mark" :class="frameClass" :style="frameStyle">
    <img v-if="src" class="linkmate-mark-img" :src="src" alt="" />
  </span>
</template>

<style scoped>
.linkmate-mark {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid var(--lx-border-subtle);
  border-radius: var(--lx-radius-lg);
  overflow: hidden;
  box-shadow: var(--lx-shadow-soft);
}

.linkmate-mark--sm {
  width: 28px;
  height: 28px;
}

.linkmate-mark--hdr {
  width: 36px;
  height: 36px;
}

.linkmate-mark--msg {
  width: 36px;
  height: 36px;
}

.linkmate-mark--lg {
  width: 96px;
  height: 96px;
  border-radius: var(--lx-radius-card);
}

.linkmate-mark-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  background: #fff;
}
</style>
