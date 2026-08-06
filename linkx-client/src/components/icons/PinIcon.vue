<script setup lang="ts">
/**
 * 全局统一的置顶图钉图标（倾斜推钉造型）。
 * 用于窗口置顶、会话置顶、公告置顶等所有「置顶」入口。
 */
withDefaults(
  defineProps<{
    size?: number
    /** 置顶激活态：实心钉头 */
    filled?: boolean
  }>(),
  {
    size: 16,
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
    viewBox="0 0 16 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <g class="pin-glyph">
      <path
        class="pin-head"
        d="M10.42 3.58a1.55 1.55 0 0 0-2.19 0L5.17 6.64a1.55 1.55 0 0 0 0 2.19l.72.72-2.24 3.88a.58.58 0 0 0 .71.88l3.88-2.24.72.72a1.55 1.55 0 0 0 2.19 0l3.06-3.06a1.55 1.55 0 0 0 0-2.19L10.42 3.58Z"
        :fill="filled ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="1.12"
        stroke-linejoin="round"
      />
      <path
        class="pin-needle"
        d="M7.35 9.55 5.75 13.15"
        stroke="currentColor"
        stroke-width="1.12"
        stroke-linecap="round"
      />
      <circle class="pin-tip" cx="5.75" cy="13.15" r="0.55" fill="currentColor" />
    </g>
  </svg>
</template>

<style scoped>
.pin-icon-svg {
  display: block;
  flex-shrink: 0;
}

.pin-glyph {
  transform-origin: 8px 8px;
  transform: rotate(-6deg);
}

.pin-icon-svg--filled .pin-needle {
  opacity: 0.92;
}
</style>
