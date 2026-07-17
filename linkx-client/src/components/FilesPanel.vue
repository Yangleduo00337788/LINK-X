<script setup lang="ts">
/**
 * 文件传输侧栏面板。
 * <p>
 * 展示聊天接收/发送的文件记录，支持分类 Tab、搜索、预览与删除。
 * </p>
 */
import { ref, computed } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { DocumentTextOutline, FolderOutline, ImageOutline, FilmOutline, TrashOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import { storeToRefs } from 'pinia'
import { useFilesStore } from '../stores/files'
import { useOverlayStore } from '../stores/overlay'
import { useSecondaryViewStore } from '../stores/secondaryView'
import type { LocalFileItem } from '../stores/files'

const message = useMessage()
const filesStore = useFilesStore()
const overlayStore = useOverlayStore()
const secondaryViewStore = useSecondaryViewStore()
const { remove } = filesStore
const { items: fileItems } = storeToRefs(filesStore)
const { activeFile } = storeToRefs(secondaryViewStore)
const { open: openOverlay } = overlayStore

/** 按 Tab 与搜索词过滤文件列表 */
const filtered = computed(() => {
  let list = fileItems.value
  if (activeTab.value !== 'recent') {
    list = list.filter(f => f.type === activeTab.value)
  }

  const q = search.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(f => f.title.toLowerCase().includes(q) || f.sender.toLowerCase().includes(q))
})

/** 按文件类型返回列表图标 */
function iconFor(type: string) {
  if (type === 'image') return ImageOutline
  if (type === 'media') return FilmOutline
  if (type === 'document') return DocumentTextOutline
  return FolderOutline
}

// 搜索关键词
const search = ref('')
// 当前 Tab：recent / document / image / media / other
const activeTab = ref('recent')

/** 切换文件分类 Tab */
function setTab(tab: string) {
  activeTab.value = tab
}

/** 选中文件（主区高亮） */
function selectFile(item: LocalFileItem) {
  activeFile.value = item
}

/** 打开 overlay 文件预览页 */
function openFilePreview(item: LocalFileItem) {
  if (!item.fileUrl) {
    message.info(`「${item.title}」暂无本地预览，请从聊天中重新发送`)
    return
  }
  openOverlay('file-preview', {
    filePreview: {
      fileName: item.title,
      fileSize: item.size,
      fileUrl: item.fileUrl,
      isImage: item.type === 'image'
    }
  })
}

/** 选中并尝试预览（有 fileUrl 时） */
function openFile(item: LocalFileItem) {
  selectFile(item)
  if (item.fileUrl) openFilePreview(item)
}

/** 删除文件记录 */
function removeFile(e: Event, id: string) {
  e.stopPropagation()
  remove(id)
  if (activeFile.value?.id === id) activeFile.value = null
  message.success('已删除文件记录')
}
</script>

<template>
  <!-- 文件列表面板 -->
  <div class="files-panel">
    <PanelSearchBar v-model="search" placeholder="搜索文件 / 发送者" />
    <!-- 分类 Tab -->
    <div class="files-tabs">
      <div class="tab-item" :class="{ active: activeTab === 'recent' }" @click="setTab('recent')">最近</div>
      <div class="tab-item" :class="{ active: activeTab === 'document' }" @click="setTab('document')">文档</div>
      <div class="tab-item" :class="{ active: activeTab === 'image' }" @click="setTab('image')">图片</div>
      <div class="tab-item" :class="{ active: activeTab === 'media' }" @click="setTab('media')">音视频</div>
      <div class="tab-item" :class="{ active: activeTab === 'other' }" @click="setTab('other')">其他</div>
    </div>
    <!-- 文件条目列表 -->
    <div class="list">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="row"
        :class="{ active: activeFile?.id === item.id }"
        @click="openFile(item)"
      >
        <div class="icon-wrap" :class="item.type">
          <n-icon :component="iconFor(item.type)" :size="24" />
        </div>
        <div class="info">
          <div class="title">{{ item.title }}</div>
          <div class="meta">
            <span>{{ item.size }}</span>
            <span class="dot">·</span>
            <span>来自: {{ item.sender }}</span>
          </div>
        </div>
        <span class="time">{{ item.time }}</span>
        <button type="button" class="del-btn" title="删除" @click="removeFile($event, item.id)">
          <n-icon :component="TrashOutline" :size="16" />
        </button>
      </div>

      <div v-if="filtered.length === 0" class="empty-state">
        没有找到相关文件
      </div>
    </div>
  </div>
</template>

<style scoped>
.files-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.files-tabs {
  display: flex;
  padding: 8px 16px 4px;
  gap: 16px;
  border-bottom: 1px solid var(--lx-border-light);
}

.tab-item {
  font-size: 13px;
  color: var(--lx-text-muted);
  cursor: pointer;
  padding-bottom: 6px;
  position: relative;
}

.tab-item.active {
  color: var(--lx-accent);
  font-weight: 500;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--lx-accent);
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.row:hover {
  background: var(--lx-bg-hover);
}

.row.active {
  background: rgba(18, 183, 245, 0.08);
}

.icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lx-bg-card);
  color: var(--lx-accent);
}

.icon-wrap.image {
  color: #52c41a;
}

.icon-wrap.media {
  color: #722ed1;
}

.info {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 14px;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: 2px;
}

.dot {
  margin: 0 4px;
}

.time {
  font-size: 12px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.del-btn {
  border: none;
  background: none;
  color: var(--lx-text-muted);
  cursor: pointer;
  padding: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.row:hover .del-btn {
  opacity: 1;
}

.del-btn:hover {
  color: var(--lx-danger);
}

.empty-state {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 40px 16px;
  font-size: 13px;
}
</style>
