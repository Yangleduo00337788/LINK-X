<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 公共图标按钮（工具栏）。样式由 .lx-action-btn / .lx-icon-btn / .lx-hdr-btn 控制。
 */
import { computed, useAttrs } from 'vue'

type LxIconButtonVariant =
  | 'default'
  | 'feed'
  | 'banner'
  | 'filled'
  | 'chat-tool'
  | 'editor'
  | 'calendar-nav'
  | 'card-icon'
  | 'viewer'
  | 'viewer-text'
  | 'hdr'
  | 'close'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    active?: boolean
    disabled?: boolean
    variant?: LxIconButtonVariant
    type?: 'button' | 'submit' | 'reset'
    title?: string
  }>(),
  {
    active: false,
    disabled: false,
    variant: 'default',
    type: 'button'
  }
)

const attrs = useAttrs()

const rootClass = computed(() => {
  const v = props.variant
  const classes: Array<string | Record<string, boolean> | unknown> = []

  if (v === 'filled') {
    classes.push('lx-icon-btn')
  } else if (v === 'hdr') {
    classes.push('lx-hdr-btn')
  } else if (v === 'close') {
    classes.push('lx-close-btn')
  } else {
    classes.push('lx-action-btn')
    if (v === 'feed') classes.push('lx-action-btn--feed')
    if (v === 'banner') classes.push('lx-action-btn--banner')
    if (v === 'chat-tool') classes.push('lx-action-btn--chat-tool')
    if (v === 'editor') classes.push('lx-action-btn--editor')
    if (v === 'calendar-nav') classes.push('lx-action-btn--calendar-nav')
    if (v === 'card-icon') classes.push('lx-action-btn--card-icon')
    if (v === 'viewer') classes.push('lx-action-btn--viewer')
    if (v === 'viewer-text') classes.push('lx-action-btn--viewer', 'lx-action-btn--viewer-text')
  }

  classes.push({ 'is-active': props.active })
  classes.push(attrs.class)
  return classes
})
</script>

<template>
  <button
    :type="type"
    :title="title"
    :class="rootClass"
    :disabled="disabled"
    v-bind="{ ...attrs, class: undefined }"
  >
    <slot />
  </button>
</template>
