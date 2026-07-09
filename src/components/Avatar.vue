<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { computed } from 'vue'
import type { Component } from 'vue'

const props = defineProps<{
  text?: string
  color: string
  size?: number
  icon?: Component
  imageUrl?: string
}>()

const size = computed(() => props.size ?? 44)
const fontSize = computed(() => `${size.value * 0.38}px`)
</script>

<template>
  <div
    class="avatar"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      backgroundColor: color,
      fontSize: fontSize
    }"
  >
    <img v-if="imageUrl" :src="imageUrl" alt="" class="avatar-img" />
    <n-icon v-else-if="icon" :component="icon" :size="size * 0.45" />
    <template v-else>{{ text }}</template>
  </div>
</template>

<style scoped>
.avatar {
  border-radius: var(--lx-avatar-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-bg-card);
  font-weight: 500;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
