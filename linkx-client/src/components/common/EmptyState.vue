<script setup lang="ts">
/**
 * 空状态占位组件。
 * <p>
 * 用于列表无数据、搜索无结果等场景，展示图标、标题、描述及可选操作插槽。
 * </p>
 */
// Naive UI 图标组件
import { NIcon } from 'naive-ui'

// 定义组件可接收的属性：图标、标题、描述（均为可选）
defineProps<{
  icon?: any // 可选的图标组件
  title?: string // 主标题文案
  description?: string // 副标题/说明文案
}>()
</script>

<template>
  <!-- 空状态容器 -->
  <div class="empty-state">
    <!-- 可选图标区域 -->
    <div v-if="icon" class="empty-icon-wrapper">
      <n-icon :component="icon" class="empty-icon" />
    </div>
    <!-- 主标题 -->
    <div v-if="title" class="empty-title">{{ title }}</div>
    <!-- 描述文字 -->
    <div v-if="description" class="empty-description">{{ description }}</div>
    <!-- 可选操作按钮插槽 -->
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
