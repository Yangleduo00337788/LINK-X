<script setup lang="ts">
// 置顶图标组件：可配置尺寸
withDefaults(
  defineProps<{
    size?: number // 图标尺寸（像素），默认 16
  }>(),
  { size: 16 }
)
</script>

<template>
  <!-- 置顶图钉 SVG 图标 -->
  <svg
    class="pin-icon-svg"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path d="M4.5 2.5h7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
    <path d="M5 2.5 3.8 9.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
    <path d="M11 2.5 12.2 9.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
    <path d="M3.8 9.2h8.4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
    <path d="M8 9.2v4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
  </svg>
</template>

<style scoped>
.pin-icon-svg {
  display: block;
  flex-shrink: 0;
}
</style>
