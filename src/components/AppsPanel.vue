<script setup lang="ts">
import { ref, computed } from 'vue'
import PanelSearchBar from './PanelSearchBar.vue'
import { apps as appList } from '../data/mockData'
import { useSecondaryView } from '../composables/useSecondaryView'
import { useOverlay } from '../composables/useOverlay'

const { activeApp } = useSecondaryView()
const { open: openOverlay } = useOverlay()
const search = ref('')

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return appList
  return appList.filter(a => a.name.includes(q) || a.desc.includes(q))
})

function openApp(app: (typeof appList)[0]) {
  activeApp.value = app
  openOverlay('app-runner', { app })
}
</script>

<template>
  <div class="apps-panel">
    <PanelSearchBar v-model="search" placeholder="搜索应用" />
    <div class="grid">
      <div
        v-for="app in filtered"
        :key="app.id"
        class="app-card"
        :class="{ active: activeApp?.id === app.id }"
        @click="openApp(app)"
      >
        <div class="app-icon" :style="{ background: app.color }">{{ app.icon }}</div>
        <div class="app-name">{{ app.name }}</div>
        <div class="app-desc">{{ app.desc }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.apps-panel {
  width: 100%;
  height: 100%;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebebeb;
  flex-shrink: 0;
}

.search-bar {
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  background: #f5f5f5;
  border-bottom: 1px solid #ebebeb;
}

.search-input {
  flex: 1;
}

.grid {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-content: start;
}

.app-card {
  padding: 16px 12px;
  border-radius: 12px;
  background: #fafafa;
  cursor: pointer;
  text-align: center;
  transition: all 0.2s;
  border: 2px solid transparent;
}

.app-card:hover {
  background: #f0f0f0;
}

.app-card.active {
  border-color: #0099ff;
  background: #e6f2ff;
}

.app-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 8px;
  border-radius: 12px;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-name {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.app-desc {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}
</style>