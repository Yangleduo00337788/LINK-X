<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PenguinWatermark from './PenguinWatermark.vue'
import AppWebView from './AppWebView.vue'
import { storeToRefs } from 'pinia'
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useOverlayStore } from '../stores/overlay'
import type { NavKey } from '../types'

const props = defineProps<{
  nav: NavKey
}>()

const router = useRouter()
const secondaryViewStore = useSecondaryViewStore()
const overlayStore = useOverlayStore()
const { activeApp, activeFavorite, activeFile } = storeToRefs(secondaryViewStore)
const { open: openOverlay } = overlayStore

const emptyHint = computed(() => {
  if (props.nav === 'contacts') return '在左侧选择联系人发起会话'
  if (props.nav === 'favorites') return '在左侧点击收藏项查看详情'
  if (props.nav === 'files') return '在左侧选择文件查看详情'
  if (props.nav === 'moments') return '在左侧浏览友链动态，可发布与评论'
  if (props.nav === 'apps') return '在左侧点击应用打开'
  return ''
})

function openFavoriteLink() {
  const fav = activeFavorite.value
  if (!fav || fav.type !== 'link') return
  const url = fav.preview.startsWith('http') ? fav.preview : `https://${fav.preview}`
  window.open(url, '_blank', 'noopener')
}

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

function openFavoriteNote() {
  if (window.electronAPI?.openNoteEditor) {
    window.electronAPI.openNoteEditor()
  } else {
    router.push('/note-editor')
  }
}

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
  <div class="placeholder-main">
    <div class="functional-region body" :class="{ 'body--embed': nav === 'apps' && activeApp }">
      <template v-if="nav === 'apps' && activeApp">
        <AppWebView
          v-if="activeApp.url"
          :url="activeApp.url"
          :title="activeApp.name"
          :app-kind="activeApp.appKind"
          :app-id="activeApp.id"
          class="app-embed"
        />
        <div v-else class="detail-card">
          <div class="big-icon" :style="{ background: activeApp.color }">{{ activeApp.icon }}</div>
          <h2>{{ activeApp.name }}</h2>
          <p>{{ activeApp.desc }}</p>
          <p class="tip">该应用暂未配置内嵌 URL。</p>
        </div>
      </template>
      <template v-else-if="nav === 'favorites' && activeFavorite">
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
