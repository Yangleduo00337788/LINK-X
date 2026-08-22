<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 置顶图钉。Win32 与窗口窗控同源（Segoe Fluent Icons）；其余平台用 SVG。
 */
import { computed } from 'vue'
import { WIN_CAPTION_GLYPH, isWindowsElectron } from '../../utils/windowCaptionGlyphs'

const props = withDefaults(
  defineProps<{
    size?: number
    /** 置顶激活态 */
    filled?: boolean
    /** 标题栏窗控：尺寸由 .lx-win-caption-btn 控制 */
    caption?: boolean
  }>(),
  {
    size: 10,
    filled: false,
    caption: false
  }
)

const useFluentGlyph = computed(() => isWindowsElectron() && !props.caption)

const glyph = computed(() =>
  props.filled ? WIN_CAPTION_GLYPH.pinned : WIN_CAPTION_GLYPH.pin
)

const fluentStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${props.size}px`
}))
</script>

<template>
  <span
    v-if="useFluentGlyph"
    class="pin-icon-fluent"
    :class="{ 'pin-icon-fluent--filled': filled }"
    :style="fluentStyle"
    aria-hidden="true"
  >
    {{ glyph }}
  </span>
  <svg
    v-else
    class="pin-icon-svg"
    :class="{ 'pin-icon-svg--filled': filled, 'pin-icon-svg--caption': caption }"
    :width="caption ? undefined : size"
    :height="caption ? undefined : size"
    :viewBox="caption ? '6 2 13 21' : '0 0 24 24'"
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
.pin-icon-fluent {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-family: 'Segoe Fluent Icons', 'Segoe MDL2 Assets', sans-serif;
  font-style: normal;
  font-weight: 400;
  line-height: 1;
  user-select: none;
  -webkit-font-smoothing: antialiased;
}

.pin-icon-svg {
  display: block;
  flex-shrink: 0;
}

.pin-icon-svg--caption {
  width: var(--lx-caption-icon-size);
  height: var(--lx-caption-icon-size);
}
</style>
