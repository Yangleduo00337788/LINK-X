<script setup lang="ts">
import { PhonePortraitOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import type { ChatMessage } from '../../../types'

defineProps<{
  msg: ChatMessage
}>()
</script>

<template>
  <div class="data-card">
    <div class="card-header">
      <div class="card-icon">
        <n-icon :component="PhonePortraitOutline" :size="20" />
      </div>
      <div class="card-info">
        <div class="card-title">{{ msg.dataCardTitle || '卡片' }}</div>
        <div class="card-sub">{{ msg.dataCardSub || '描述信息' }}</div>
      </div>
      <div class="card-tag">套餐</div>
    </div>
    <div class="card-divider" />
    <div class="card-body">
      <div class="card-label">{{ msg.dataCardLabel || '标签' }}</div>
      <div class="card-value">{{ msg.dataCardValue || '-' }}</div>
    </div>
  </div>
</template>

<style scoped>
.data-card {
  width: 240px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: 0 2px 8px var(--lx-shadow-soft, rgba(0, 0, 0, 0.08));
}
.card-header {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  gap: 10px;
}
.card-icon {
  width: 36px;
  height: 36px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}
.card-sub {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: 2px;
}
.card-tag {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: 4px;
  flex-shrink: 0;
}
.card-divider {
  height: 1px;
  background: var(--lx-border-light);
  margin: 0 14px;
}
.card-body {
  padding: 10px 14px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-label {
  font-size: 13px;
  color: var(--lx-text-secondary);
}
.card-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}
</style>
