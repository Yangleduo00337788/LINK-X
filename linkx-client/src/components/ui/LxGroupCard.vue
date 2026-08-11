<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 公共分组卡片容器。样式由 .lx-group-card 系列控制。
 */
type LxGroupCardVariant = 'default' | 'flat' | 'settings'

const { variant = 'default', tag = 'div' } = defineProps<{
  variant?: LxGroupCardVariant
  tag?: string
}>()

const variantClass = {
  default: 'lx-group-card',
  flat: 'lx-group-card lx-group-card--flat',
  settings: 'lx-group-card lx-group-card--settings group-card'
} as const
</script>

<template>
  <component :is="tag" :class="variantClass[variant]">
    <slot />
  </component>
</template>
