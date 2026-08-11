<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 数据卡片消息气泡（如套餐/流量卡样式）。
 */
import { PhonePortraitOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'

defineProps<{
  msg: ChatMessage
}>()

const { t } = useI18n()
</script>

<template>
  <div class="data-card">
    <div class="card-header">
      <div class="card-icon">
        <n-icon :component="PhonePortraitOutline" :size="20" />
      </div>
      <div class="card-info">
        <div class="card-title">{{ msg.dataCardTitle || t('modals.dataCardTitle') }}</div>
        <div class="card-sub">{{ msg.dataCardSub || t('modals.dataCardSub') }}</div>
      </div>
      <div class="card-tag">{{ t('modals.dataCardTag') }}</div>
    </div>
    <div class="card-divider" />
    <div class="card-body">
      <div class="card-label">{{ msg.dataCardLabel || t('modals.dataCardLabel') }}</div>
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
  padding: var(--lx-space-lg) var(--lx-space-xl);
  gap: var(--lx-space-md);
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
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
}
.card-sub {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-2xs);
}
.card-tag {
  font-size: var(--lx-font-xs);
  padding: var(--lx-space-2xs) var(--lx-space-sm);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: var(--lx-radius-2xs);
  flex-shrink: 0;
}
.card-divider {
  height: 1px;
  background: var(--lx-border-light);
  margin: 0 var(--lx-space-xl);
}
.card-body {
  padding: var(--lx-space-md) var(--lx-space-xl) var(--lx-space-xl);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-label {
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
}
.card-value {
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
}
</style>
