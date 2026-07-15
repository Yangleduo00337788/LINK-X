<script setup lang="ts">
/**
 * 应用中心侧栏面板。
 * 展示内置应用列表，支持搜索与选中后在主区打开 AppWebView。
 */
import { ref, computed } from 'vue'
import PanelSearchBar from './PanelSearchBar.vue'
import { storeToRefs } from 'pinia'
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useAppStore } from '../stores/app'
import type { AppItem } from '../types'

/** 内置应用列表 */
const apps: AppItem[] = [
  { id: 'netease-music', name: '网易云音乐', desc: '登录后播放音乐', icon: '云', color: '#c20c0c', url: 'https://music.163.com/' },
  { id: 'douyin', name: '抖音', desc: '登录后刷短视频', icon: '抖', color: '#111111', url: 'https://www.douyin.com/' }
]

const secondaryViewStore = useSecondaryViewStore()
const appStore = useAppStore()
const { activeApp } = storeToRefs(secondaryViewStore)
const { setNav } = appStore
// 应用搜索关键词
const search = ref('')

/** 按名称或描述过滤应用列表 */
const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return apps
  return apps.filter(a => a.name.toLowerCase().includes(q) || a.desc.toLowerCase().includes(q))
})

/** 选中应用并切换到 apps 主导航 */
function openApp(app: AppItem) {
  activeApp.value = app
  setNav('apps')
}
</script>

<template>
  <!-- 应用列表面板 -->
  <div class="apps-panel">
    <PanelSearchBar v-model="search" placeholder="搜索应用" />
    <!-- 应用卡片网格 -->
    <div class="grid">
      <button
        v-for="app in filtered"
        :key="app.id"
        type="button"
        class="app-card"
        :class="{ active: activeApp?.id === app.id }"
        @click="openApp(app)"
      >
        <div class="app-icon" :style="{ background: app.color }">{{ app.icon }}</div>
        <span class="app-name">{{ app.name }}</span>
        <span class="app-desc">{{ app.desc }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.apps-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.grid {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  align-content: start;
}

.app-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 12px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-card);
  cursor: pointer;
  transition: background 0.15s;
  text-align: center;
}

.app-card:hover,
.app-card.active {
  background: var(--lx-accent-soft);
}

.app-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-radius);
  color: var(--lx-bg-card);
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.app-desc {
  font-size: 11px;
  color: var(--lx-text-muted);
}
</style>
