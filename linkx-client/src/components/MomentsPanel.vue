<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
/**
 * 中间列友链面板：Electron 开独立窗；Web 仅作提示（主栏渲染 MomentsMainView）。
 */
import { onMounted } from 'vue'
import { useAppStore } from '../stores/app'
import { useMomentsStore } from '../stores/moments'
import { useI18n } from '../i18n'

const appStore = useAppStore()
const momentsStore = useMomentsStore()
const { t } = useI18n()

onMounted(() => {
  if (!momentsStore.initialized) {
    void momentsStore.fetchMoments()
  }
  if (window.electronAPI?.openMoments) {
    window.electronAPI.openMoments()
    appStore.setNav('chat')
  }
})
</script>

<template>
  <div class="moments-panel">
    <div class="empty">
      <h3>{{ t('moments.webFeedTitle') }}</h3>
      <p>{{ t('moments.webFeedHint') }}</p>
    </div>
  </div>
</template>

<style scoped>
.moments-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty {
  color: var(--lx-text-muted);
  text-align: center;
  padding: 16px;
}
.empty h3 {
  font-size: 14px;
  margin: 0 0 6px;
}
.empty p {
  font-size: 12px;
  margin: 0;
  line-height: 1.5;
}
</style>
