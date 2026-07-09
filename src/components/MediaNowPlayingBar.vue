<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { MusicalNotesOutline, ChevronForwardOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useMediaPlaybackStore } from '../stores/mediaPlayback'
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useAppStore } from '../stores/app'

const mediaStore = useMediaPlaybackStore()
const secondaryViewStore = useSecondaryViewStore()
const appStore = useAppStore()
const { isPlaying, displayLabel } = storeToRefs(mediaStore)
const { setNav } = appStore
const { activeApp } = storeToRefs(secondaryViewStore)

function jumpToSource() {
  const app = mediaStore.sourceApp
  if (!app) return
  setNav('apps')
  activeApp.value = app
}
</script>

<template>
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
