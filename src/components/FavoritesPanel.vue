<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import { DocumentTextOutline, ImageOutline, LinkOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import { favorites as favList } from '../data/mockData'
import { useSecondaryView } from '../composables/useSecondaryView'
import type { FavoriteItem } from '../types'

const { activeFavorite } = useSecondaryView()
const search = ref('')

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return favList
  return favList.filter(f => f.title.toLowerCase().includes(q) || f.preview.toLowerCase().includes(q))
})

function iconFor(type: FavoriteItem['type']) {
  if (type === 'image') return ImageOutline
  if (type === 'link') return LinkOutline
  return DocumentTextOutline
}

function openItem(item: FavoriteItem) {
  activeFavorite.value = item
}
</script>

<template>
  <div class="fav-panel">
    <PanelSearchBar v-model="search" placeholder="搜索收藏" />
    <div class="list">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="row"
        :class="{ active: activeFavorite?.id === item.id }"
        @click="openItem(item)"
      >
        <div class="icon-wrap">
          <n-icon :component="iconFor(item.type)" :size="22" color="#0099ff" />
        </div>
        <div class="info">
          <div class="title">{{ item.title }}</div>
          <div class="preview">{{ item.preview }}</div>
        </div>
        <span class="time">{{ item.time }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fav-panel {
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

.list {
  flex: 1;
  overflow-y: auto;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.row:hover,
.row.active {
  background: #e6f2ff;
}

.icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #e6f2ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.info {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 14px;
  color: #333;
}

.preview {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  font-size: 11px;
  color: #bbb;
  flex-shrink: 0;
}
</style>