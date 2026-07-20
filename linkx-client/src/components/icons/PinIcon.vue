<script setup lang="ts">
/**
 * 全局统一的垂直图钉图标（不倾斜）。
 * 用于窗口置顶、会话置顶等所有「置顶」入口。
 */
withDefaults(
  defineProps<{
    size?: number // 图标尺寸（像素），默认 16
  }>(),
  { size: 16 }
)
</script>

<template>
  <svg
    class="pin-icon-svg"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <!-- 顶横线 -->
    <path d="M4.5 2.5h7" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
    <!-- 左侧轻外扩 -->
    <path d="M5 2.5 3.8 9.2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
    <!-- 右侧轻外扩 -->
    <path d="M11 2.5 12.2 9.2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
    <!-- 底横线 -->
    <path d="M3.8 9.2h8.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
    <!-- 垂直短针 -->
    <path d="M8 9.2v4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
  </svg>
</template>

<style scoped>
.pin-icon-svg {
  display: block;
  flex-shrink: 0;
  transform: none;
}
</style>
