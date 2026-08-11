<!-- 作者：yangleduo -->
<script setup lang="ts">
// Vue 计算属性
import { computed } from 'vue'
// Vue Router
import { useRouter } from 'vue-router'
// 空状态 Logo 占位组件
import PenguinWatermark from './PenguinWatermark.vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 次级视图 Store
import { useSecondaryViewStore } from '../stores/secondaryView'
// 全屏覆盖层 Store
import { useOverlayStore } from '../stores/overlay'
// 主导航键类型
import type { NavKey } from '../types'
import { useI18n } from '../i18n'
import { LxButton } from './ui'

// 接收当前主导航键
const props = defineProps<{
  nav: NavKey
}>()

const { t } = useI18n()
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
  if (props.nav === 'contacts') return t('placeholder.selectContacts')
  if (props.nav === 'favorites') return t('placeholder.selectFavorite')
  if (props.nav === 'files') return t('placeholder.selectFile')
  if (props.nav === 'moments') return t('placeholder.selectMoments')
  return t('placeholder.selectLeft')
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
          <p class="meta">{{ t('placeholder.updatedAt', { time: activeFavorite.time }) }}</p>
          <div class="fav-actions">
            <LxButton
              v-if="activeFavorite.type === 'link'"
              variant="primary-comfortable"
              @click="openFavoriteLink"
            >
              {{ t('placeholder.openLink') }}
            </LxButton>
            <LxButton
              v-if="activeFavorite.type === 'image'"
              variant="primary-comfortable"
              @click="previewFavoriteImage"
            >
              {{ t('placeholder.previewImage') }}
            </LxButton>
            <LxButton
              v-if="activeFavorite.type === 'note'"
              variant="primary-comfortable"
              @click="openFavoriteNote"
            >
              {{ t('placeholder.openNote') }}
            </LxButton>
          </div>
        </div>
      </template>
      <!-- 文件详情卡片 -->
      <template v-else-if="nav === 'files' && activeFile">
        <div class="detail-card">
          <h2>{{ activeFile.title }}</h2>
          <p class="meta">{{ t('placeholder.fromSender', { size: activeFile.size, name: activeFile.sender }) }}</p>
          <p class="meta">{{ t('placeholder.receivedAt', { time: activeFile.time }) }}</p>
          <div v-if="activeFile.type === 'image' && activeFile.fileUrl" class="file-preview-wrap">
            <img :src="activeFile.fileUrl" :alt="activeFile.title" class="file-preview-img" />
          </div>
          <div class="fav-actions">
            <LxButton
              v-if="activeFile.fileUrl"
              variant="primary-comfortable"
              @click="previewActiveFile"
            >
              {{ t('placeholder.fullscreenPreview') }}
            </LxButton>
          </div>
        </div>
      </template>
      <!-- 默认：空状态 Logo -->
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
  padding: var(--lx-space-4xl);
  display: flex;
  flex-direction: column;
  position: relative;
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
  padding: var(--lx-space-4xl);
  box-shadow: var(--lx-shadow-card);
}

.detail-card h2 {
  margin: 0 0 var(--lx-space-lg);
  font-size: var(--lx-font-4xl);
  color: var(--lx-text-body);
}

.detail-card p {
  margin: 0 0 var(--lx-space);
  color: var(--lx-text-secondary);
  line-height: var(--lx-leading-relaxed);
}

.meta {
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.tip {
  margin-top: var(--lx-space-2xl) !important;
  font-size: var(--lx-font-sm);
  color: var(--lx-accent);
}

.big-icon {
  width: 72px;
  height: 72px;
  border-radius: var(--lx-radius);
  color: var(--lx-bg-card);
  font-size: var(--lx-font-7xl);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--lx-space-2xl);
}

.preview {
  white-space: pre-wrap;
  word-break: break-all;
}

.fav-actions {
  margin-top: var(--lx-space-2xl);
  display: flex;
  gap: var(--lx-space);
  flex-wrap: wrap;
}

.file-preview-wrap {
  margin: var(--lx-space-2xl) 0;
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
