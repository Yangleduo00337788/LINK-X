<script setup lang="ts">
// Naive UI 图标组件
import { NIcon } from 'naive-ui'
// Ionicons5 音乐与箭头图标
import { MusicalNotesOutline, ChevronForwardOutline } from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 媒体播放状态 Store
import { useMediaPlaybackStore } from '../stores/mediaPlayback'
// 次级视图 Store（应用面板选中项）
import { useSecondaryViewStore } from '../stores/secondaryView'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'

// 媒体播放 Store 实例
const mediaStore = useMediaPlaybackStore()
// 次级视图 Store 实例
const secondaryViewStore = useSecondaryViewStore()
// 应用 Store 实例
const appStore = useAppStore()
// 是否正在播放、显示标签文案
const { isPlaying, displayLabel } = storeToRefs(mediaStore)
// 切换主导航的方法
const { setNav } = appStore
// 当前选中的应用
const { activeApp } = storeToRefs(secondaryViewStore)

// 点击跳转到音乐来源应用
function jumpToSource() {
  const app = mediaStore.sourceApp
  if (!app) return
  setNav('apps')
  activeApp.value = app
}
</script>

<template>
  <!-- 正在播放栏：有音乐播放时显示 -->
  <button
    v-if="isPlaying"
    type="button"
    class="now-playing-bar"
    title="点击跳转到网易云音乐"
    @click="jumpToSource"
  >
    <span class="np-icon-wrap">
      <n-icon :component="MusicalNotesOutline" :size="16" class="np-icon" />
    </span>
    <span class="np-text">
      <span class="np-label">正在播放</span>
      <span class="np-track">{{ displayLabel }}</span>
    </span>
    <n-icon :component="ChevronForwardOutline" :size="16" class="np-arrow" />
  </button>
</template>

<style scoped>
.now-playing-bar {
  flex-shrink: 0;
  width: 100%;
  border: none;
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  padding: 0 14px;
  height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: background 0.15s;
  text-align: left;
}

.now-playing-bar:hover {
  background: var(--lx-accent-soft);
}

.np-icon-wrap {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #c20c0c;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.np-icon {
  color: #fff;
  animation: np-bounce 1.2s ease-in-out infinite;
}

@keyframes np-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

.np-text {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.np-label {
  font-size: 12px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.np-track {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.np-arrow {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}
</style>
