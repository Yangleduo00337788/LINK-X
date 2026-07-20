<script setup lang="ts">
/**
 * 内嵌于 AppShell 侧栏的友链面板。
 *
 * 入口处不再提供发布编辑器,统一通过 window.electronAPI.openMoments
 * 打开独立窗口(与 Sidebar 一致的行为)。
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
  // 桌面板式下,直接打开独立窗口
  if (window.electronAPI?.openMoments) {
    window.electronAPI.openMoments()
    appStore.setNav('chat') // 回到聊天页避免空白
  }
})
</script>

<template>
  <div class="moments-panel">
    <div class="empty">
      <h3>{{ t('moments.opening') }}</h3>
      <p>{{ t('moments.openingHint') }}</p>
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
}
.empty h3 {
  font-size: 14px;
  margin: 0 0 6px;
}
.empty p {
  font-size: 12px;
  margin: 0;
}
</style>
