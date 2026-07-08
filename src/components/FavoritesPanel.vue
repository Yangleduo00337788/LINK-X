<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import { DocumentTextOutline, ImageOutline, LinkOutline, FolderOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import { storeToRefs } from 'pinia'
import { useFavoritesStore } from '../stores/favorites'
import { useSecondaryViewStore } from '../stores/secondaryView'
import type { FavoriteItem } from '../types'

const favoritesStore = useFavoritesStore()
const secondaryViewStore = useSecondaryViewStore()
const { items: favList } = storeToRefs(favoritesStore)
const { activeFavorite } = storeToRefs(secondaryViewStore)
const search = ref('')
const activeTab = ref('all') // all, link, image, file, note

const addOptions = [
  { label: '写笔记', key: 'note' }
]

function onAddSelect(key: string) {
  if (key === 'note') {
    if (window.electronAPI) {
      window.electronAPI.openNoteEditor()
    } else {
      // 浏览器环境 fallback，如打开一个 modal 等
      window.location.hash = 'note-editor'
    }
  }
}

const filtered = computed(() => {
  let list = favList.value
  if (activeTab.value !== 'all') {
    list = list.filter(f => f.type === activeTab.value)
  }

  const q = search.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(f => f.title.toLowerCase().includes(q) || f.preview.toLowerCase().includes(q))
})

function iconFor(type: FavoriteItem['type']) {
  if (type === 'image') return ImageOutline
  if (type === 'link') return LinkOutline
  if (type === 'file') return FolderOutline
  return DocumentTextOutline
}

function openItem(item: FavoriteItem) {
  activeFavorite.value = item
}

function setTab(tab: string) {
  activeTab.value = tab
}
</script>

<template>
  <div class="fav-panel">
    <PanelSearchBar
      v-model="search"
      placeholder="搜索收藏"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />
    <div class="fav-tabs">
      <div class="tab-item" :class="{ active: activeTab === 'all' }" @click="setTab('all')">全部</div>
      <div class="tab-item" :class="{ active: activeTab === 'link' }" @click="setTab('link')">链接</div>
      <div class="tab-item" :class="{ active: activeTab === 'image' }" @click="setTab('image')">图片</div>
      <div class="tab-item" :class="{ active: activeTab === 'file' }" @click="setTab('file')">文件</div>
      <div class="tab-item" :class="{ active: activeTab === 'note' }" @click="setTab('note')">笔记</div>
    </div>
    <div class="list">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="row"
        :class="{ active: activeFavorite?.id === item.id }"
        @click="openItem(item)"
      >
        <div class="icon-wrap" :class="item.type">
          <n-icon :component="iconFor(item.type)" :size="20" />
        </div>
        <div class="info">
          <div class="title">{{ item.title }}</div>
          <div class="preview">{{ item.preview }}</div>
        </div>
        <span class="time">{{ item.time }}</span>
      </div>
      
      <div v-if="filtered.length === 0" class="empty-state">
        无匹配的收藏内容
      </div>
    </div>
  </div>
</template>

<style scoped>
.fav-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.fav-tabs {
  display: flex;
  padding: 8px 16px 4px;
  gap: 16px;
  border-bottom: 1px solid var(--lx-bg-hover);
}

.tab-item {
  font-size: 13px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  padding-bottom: 6px;
  position: relative;
  transition: color 0.2s;
}

.tab-item:hover {
  color: var(--lx-text-body);
}

.tab-item.active {
  color: var(--lx-accent);
  font-weight: 500;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 10%;
  width: 80%;
  height: 2px;
  background: var(--lx-accent);
  border-radius: 2px;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  cursor: pointer;
  border-radius: var(--lx-radius);
  margin-bottom: 4px;
  transition: background 0.2s;
}

.row:hover {
  background: var(--lx-bg-hover);
}

.row.active {
  background: rgba(18, 183, 245, 0.1);
}

.icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-wrap.link { background: var(--lx-accent-bg-soft); color: var(--lx-accent); }
.icon-wrap.image { background: #fff0e6; color: #ff8800; }
.icon-wrap.file { background: #e6ffed; color: #00cc44; }
.icon-wrap.note { background: #f2e6ff; color: #8800ff; }

.info {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 14px;
  color: var(--lx-text-body);
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preview {
  font-size: 12px;
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  font-size: 11px;
  color: #bbb;
  flex-shrink: 0;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--lx-text-muted);
  font-size: 13px;
}
</style>