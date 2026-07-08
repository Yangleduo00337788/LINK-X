<script setup lang="ts">
import { NIcon } from 'naive-ui'

defineProps<{
  icon?: any
  title?: string
  description?: string
}>()
</script>

<template>
  <div class="empty-state">
    <div v-if="icon" class="empty-icon-wrapper">
      <n-icon :component="icon" class="empty-icon" />
    </div>
    <div v-if="title" class="empty-title">{{ title }}</div>
    <div v-if="description" class="empty-description">{{ description }}</div>
    <slot name="action"></slot>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 32px;
  text-align: center;
  color: var(--lx-text-muted, var(--lx-text-muted));
  background: transparent;
}

.empty-icon-wrapper {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--lx-bg-hover);
}

.empty-icon {
  font-size: 32px;
  color: #b0b0b5;
}

.empty-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--lx-text, var(--lx-text));
  margin-bottom: 6px;
}

.empty-description {
  font-size: 13px;
  line-height: 1.5;
  max-width: 240px;
}
</style>
