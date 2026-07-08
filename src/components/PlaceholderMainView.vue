<script setup lang="ts">
import { computed } from 'vue'
import PenguinWatermark from './PenguinWatermark.vue'
import AppWebView from './AppWebView.vue'
import { storeToRefs } from 'pinia'
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useOverlayStore } from '../stores/overlay'
import type { NavKey } from '../types'

const props = defineProps<{
  nav: NavKey
}>()

const secondaryViewStore = useSecondaryViewStore()
const overlayStore = useOverlayStore()
const { activeApp, activeFavorite } = storeToRefs(secondaryViewStore)
const { open: openOverlay } = overlayStore

const emptyHint = computed(() => {
  if (props.nav === 'contacts') return '在左侧选择联系人发起会话'
  if (props.nav === 'favorites') return '在左侧点击收藏项查看详情'
  if (props.nav === 'moments') return '浏览左侧友链动态'
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
</script>

<template>
  <div class="placeholder-main">
    <div class="functional-region body">
      <template v-if="nav === 'apps' && activeApp">
        <AppWebView
          v-if="activeApp.url"
          :url="activeApp.url"
          :title="activeApp.name"
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
</style>
