<script setup lang="ts">
/**
 * 收藏侧栏面板。
 * <p>
 * 按类型 Tab 与关键词过滤收藏项，支持打开笔记编辑器与删除收藏。
 * </p>
 */
import { ref, computed } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { DocumentTextOutline, ImageOutline, LinkOutline, FolderOutline, TrashOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import { storeToRefs } from 'pinia'
import { useFavoritesStore } from '../stores/favorites'
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useRouter } from 'vue-router'
import type { FavoriteItem } from '../types'

const favoritesStore = useFavoritesStore()
const secondaryViewStore = useSecondaryViewStore()
const router = useRouter()
const message = useMessage()
const { remove } = favoritesStore
const { items: favList } = storeToRefs(favoritesStore)
const { activeFavorite } = storeToRefs(secondaryViewStore)
// 搜索关键词
const search = ref('')
// 当前类型 Tab：all / link / image / file / note
const activeTab = ref('all')

// 搜索栏「添加」下拉选项
const addOptions = [
  { label: '写笔记', key: 'note' }
]

/** 处理添加菜单：Electron 开独立窗口，Web 走路由 */
function onAddSelect(key: string) {
  if (key === 'note') {
    if (window.electronAPI) {
      window.electronAPI.openNoteEditor()
    } else {
      router.push('/note-editor')
    }
  }
}

/** 按 Tab 与搜索词过滤后的收藏列表 */
const filtered = computed(() => {
  let list = favList.value
  if (activeTab.value !== 'all') {
    list = list.filter(f => f.type === activeTab.value)
  }

  const q = search.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(f => f.title.toLowerCase().includes(q) || f.preview.toLowerCase().includes(q))
})

/** 按收藏类型返回对应图标组件 */
function iconFor(type: FavoriteItem['type']) {
  if (type === 'image') return ImageOutline
  if (type === 'link') return LinkOutline
  if (type === 'file') return FolderOutline
  return DocumentTextOutline
}

/** 选中收藏项并在主区展示详情 */
function openItem(item: FavoriteItem) {
  activeFavorite.value = item
}

/** 删除收藏；若删的是当前选中项则清空 activeFavorite */
function removeItem(e: Event, id: string) {
  e.stopPropagation()
  remove(id)
  if (activeFavorite.value?.id === id) activeFavorite.value = null
  message.success('已删除收藏')
}

/** 切换类型 Tab */
function setTab(tab: string) {
  activeTab.value = tab
}
</script>

<template>
  <!-- 收藏列表面板 -->
  <div class="fav-panel">
    <!-- 搜索与添加笔记 -->
    <PanelSearchBar
      v-model="search"
      placeholder="搜索收藏"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />
    <!-- 类型 Tab -->
    <div class="fav-tabs">
      <div class="tab-item" :class="{ active: activeTab === 'all' }" @click="setTab('all')">全部</div>
      <div class="tab-item" :class="{ active: activeTab === 'link' }" @click="setTab('link')">链接</div>
      <div class="tab-item" :class="{ active: activeTab === 'image' }" @click="setTab('image')">图片</div>
      <div class="tab-item" :class="{ active: activeTab === 'file' }" @click="setTab('file')">文件</div>
      <div class="tab-item" :class="{ active: activeTab === 'note' }" @click="setTab('note')">笔记</div>
    </div>
    <!-- 收藏条目列表 -->
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
        <button type="button" class="del-btn" title="删除" @click="removeItem($event, item.id)">
          <n-icon :component="TrashOutline" :size="16" />
        </button>
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
.icon-wrap.image { background: var(--lx-icon-image-bg); color: var(--lx-icon-image-color); }
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
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.del-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--lx-radius);
  opacity: 0;
  transition: opacity 0.15s;
}

.row:hover .del-btn {
  opacity: 1;
}

.del-btn:hover {
  color: var(--lx-danger);
  background: rgba(250, 81, 81, 0.1);
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--lx-text-muted);
  font-size: 13px;
}
</style>
