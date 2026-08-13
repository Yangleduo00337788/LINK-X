<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 置顶图钉（Material push_pin 造型：扁头 + 竖针 + 底叉）。
 * 列表为灰色线框感；窗口置顶激活时 filled 实心 + 主题色。
 */
withDefaults(
  defineProps<{
    size?: number
    /** 置顶激活态（窗口置顶已开启） */
    filled?: boolean
  }>(),
  {
    size: 10,
    filled: false
  }
)
</script>

<template>
  <svg
    class="pin-icon-svg"
    :class="{ 'pin-icon-svg--filled': filled }"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      fill="currentColor"
      :opacity="filled ? 1 : 0.92"
      d="M16 9V4h1c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z"
    />
  </svg>
</template>

<style scoped>
.pin-icon-svg {
  display: block;
  flex-shrink: 0;
}
</style>
