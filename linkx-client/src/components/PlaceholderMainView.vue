<script setup lang="ts">
// Vue 计算属性
import { computed } from 'vue'
// Vue Router
import { useRouter } from 'vue-router'
// 企鹅水印占位组件
import PenguinWatermark from './PenguinWatermark.vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 次级视图 Store
import { useSecondaryViewStore } from '../stores/secondaryView'
// 全屏覆盖层 Store
import { useOverlayStore } from '../stores/overlay'
// 主导航键类型
import type { NavKey } from '../types'

// 接收当前主导航键
const props = defineProps<{
  nav: NavKey
}>()

// 路由实例
const router = useRouter()
// 次级视图 Store 实例
const secondaryViewStore = useSecondaryViewStore()
// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 当前选中的收藏、文件
const { activeFavorite, activeFile } = storeToRefs(secondaryViewStore)
// 打开覆盖层的方法
const { open: openOverlay } = overlayStore

// 根据导航键生成空状态提示文案
const emptyHint = computed(() => {
  if (props.nav === 'contacts') return '在左侧选择联系人发起会话'
  if (props.nav === 'favorites') return '在左侧点击收藏项查看详情'
  if (props.nav === 'files') return '在左侧选择文件查看详情'
  if (props.nav === 'moments') return '在左侧浏览友链动态，可发布与评论'
  return ''
})

// 打开收藏中的链接（新窗口）
function openFavoriteLink() {
  const fav = activeFavorite.value
  if (!fav || fav.type !== 'link') return
  const url = fav.preview.startsWith('http') ? fav.preview : `https://${fav.preview}`
  window.open(url, '_blank', 'noopener')
}

// 预览收藏中的图片
function previewFavoriteImage() {
  const fav = activeFavorite.value
  if (!fav || fav.type !== 'image') return
  openOverlay('file-preview', {
    filePreview: {
      fileName: fav.title,
      fileUrl: fav.preview.startsWith('data:') || fav.preview.startsWith('http') ? fav.preview : undefined,
      isImage: true
    }
  })
}

// 打开笔记编辑器（Electron 独立窗口或路由跳转）
function openFavoriteNote() {
  if (window.electronAPI?.openNoteEditor) {
    window.electronAPI.openNoteEditor()
  } else {
    router.push('/note-editor')
  }
}

// 全屏预览当前选中的文件
function previewActiveFile() {
  const file = activeFile.value
  if (!file?.fileUrl) return
  openOverlay('file-preview', {
    filePreview: {
      fileName: file.title,
      fileSize: file.size,
      fileUrl: file.fileUrl,
      isImage: file.type === 'image'
    }
  })
}
</script>

<template>
  <!-- 占位主视图：根据 nav 与选中项展示内容或水印 -->
  <div class="placeholder-main">
    <!-- 功能区域主体 -->
    <div class="functional-region body">
      <!-- 收藏详情卡片 -->
      <template v-if="nav === 'favorites' && activeFavorite">
        <div class="detail-card">
          <h2>{{ activeFavorite.title }}</h2>
          <p class="preview">{{ activeFavorite.preview }}</p>
          <p class="meta">更新于 {{ activeFavorite.time }}</p>
          <div class="fav-actions">
            <button
              v-if="activeFavorite.type === 'link'"
              type="button"
              class="action-btn"
              @click="openFavoriteLink"
            >
              打开链接
            </button>
            <button
              v-if="activeFavorite.type === 'image'"
              type="button"
              class="action-btn"
              @click="previewFavoriteImage"
            >
              预览图片
            </button>
            <button
              v-if="activeFavorite.type === 'note'"
              type="button"
              class="action-btn"
              @click="openFavoriteNote"
            >
              打开笔记编辑器
            </button>
          </div>
        </div>
      </template>
      <!-- 文件详情卡片 -->
      <template v-else-if="nav === 'files' && activeFile">
        <div class="detail-card">
          <h2>{{ activeFile.title }}</h2>
          <p class="meta">{{ activeFile.size }} · 来自 {{ activeFile.sender }}</p>
          <p class="meta">接收时间 {{ activeFile.time }}</p>
          <div v-if="activeFile.type === 'image' && activeFile.fileUrl" class="file-preview-wrap">
            <img :src="activeFile.fileUrl" :alt="activeFile.title" class="file-preview-img" />
          </div>
          <div class="fav-actions">
            <button
              v-if="activeFile.fileUrl"
              type="button"
              class="action-btn"
              @click="previewActiveFile"
            >
              全屏预览
            </button>
          </div>
        </div>
      </template>
      <!-- 默认：企鹅水印空状态 -->
      <template v-else>
        <PenguinWatermark :hint="emptyHint" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.placeholder-main {
  flex: 1;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.functional-region.body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.functional-region.body.body--embed {
  padding: 0;
}

.app-embed {
  flex: 1;
  min-height: 0;
}

.detail-card {
  max-width: 480px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  padding: 24px;
  box-shadow: var(--lx-shadow-card);
}

.detail-card h2 {
  margin: 0 0 12px;
  font-size: 20px;
  color: var(--lx-text-body);
}

.detail-card p {
  margin: 0 0 8px;
  color: var(--lx-text-secondary);
  line-height: 1.6;
}

.meta {
  font-size: 13px;
  color: var(--lx-text-muted);
}

.tip {
  margin-top: 16px !important;
  font-size: 12px;
  color: var(--lx-accent);
}

.big-icon {
  width: 72px;
  height: 72px;
  border-radius: var(--lx-radius);
  color: var(--lx-bg-card);
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.preview {
  white-space: pre-wrap;
  word-break: break-all;
}

.fav-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.action-btn {
  border: none;
  background: var(--lx-accent);
  color: #fff;
  border-radius: var(--lx-radius);
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
}

.action-btn:hover {
  opacity: 0.9;
}

.file-preview-wrap {
  margin: 16px 0;
  border-radius: var(--lx-radius);
  overflow: hidden;
  border: 1px solid var(--lx-border-light);
}

.file-preview-img {
  display: block;
  max-width: 100%;
  max-height: 320px;
  object-fit: contain;
  background: var(--lx-bg-panel);
}
</style>
