<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
/**
 * 群相册弹窗（群聊顶栏「应用」→「群相册」）。
 * - 创建相册：输入名称，生成空相册
 * - 上传：Electron 走原生选图；Web 回退 file input
 */
import { ref, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useOverlayStore } from '../../stores/overlay'
import { useMessage } from 'naive-ui'
import { useI18n } from '../../i18n'
import axios from 'axios'
import { DEFAULT_GROUP_ALBUM_NAME_ZH } from '../../constants/groupAlbum'
import GroupAlbumAuthImage from './GroupAlbumAuthImage.vue'
import { LxButton } from '../ui'
import ModalWinHeadActions from '../ModalWinHeadActions.vue'

const message = useMessage()
const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const overlayStore = useOverlayStore()
const { groupAlbumOpen } = storeToRefs(chatModalsStore)
const { closeGroupAlbum } = chatModalsStore
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)
const { open: openOverlay } = overlayStore

const tab = ref<'feed' | 'albums' | 'me'>('feed')
const uploading = ref(false)
const createOpen = ref(false)
const newAlbumName = ref('')
const selectedAlbum = ref(DEFAULT_GROUP_ALBUM_NAME_ZH)
const fileInputRef = ref<HTMLInputElement | null>(null)

const albumFolders = computed(() => {
  const id = currentSessionId.value
  return id ? groupMetaStore.albumFoldersFor(id) : []
})

const albumItems = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  let list = groupMetaStore.albumFor(id)
  if (tab.value === 'me') {
    const myId = userProfile.value.userId
    const myName = userProfile.value.nickname
    list = list.filter(i => (myId && i.uploaderId === myId) || (!!myName && i.user === myName))
  } else if (tab.value === 'feed' && selectedAlbum.value) {
    list = list.filter(i => (i.albumName || DEFAULT_GROUP_ALBUM_NAME_ZH) === selectedAlbum.value)
  }
  return list
})

watch(groupAlbumOpen, open => {
  if (open) {
    tab.value = 'feed'
    selectedAlbum.value = DEFAULT_GROUP_ALBUM_NAME_ZH
    createOpen.value = false
    newAlbumName.value = ''
    const id = currentSessionId.value
    if (id) void groupMetaStore.fetchAlbum(id)
  }
})

function close() {
  if (uploading.value) return
  closeGroupAlbum()
}

function openCreateAlbum() {
  createOpen.value = true
  newAlbumName.value = ''
  tab.value = 'albums'
}

function confirmCreateAlbum() {
  const id = currentSessionId.value
  const name = newAlbumName.value.trim()
  if (!id) {
    message.error(t('extra.opFail'))
    return
  }
  if (!name) {
    message.warning(t('extra.albumNameRequired'))
    return
  }
  if (name.length > 32) {
    message.warning(t('extra.albumNameTooLong'))
    return
  }
  const ok = groupMetaStore.createAlbumFolder(id, name)
  if (!ok) {
    message.error(t('extra.opFail'))
    return
  }
  selectedAlbum.value = name
  createOpen.value = false
  newAlbumName.value = ''
  message.success(t('extra.albumCreated', { name }))
}

async function pickAndUpload() {
  const sessionId = currentSessionId.value
  if (!sessionId) {
    message.error(t('extra.opFail'))
    return
  }

  // Electron：原生对话框（可靠）；Web：隐藏 input
  const pick = window.electronAPI?.pickImages
  if (pick) {
    try {
      const picked = await pick()
      if (!picked?.length) return
      const files = picked.map(
        p => new File([p.data instanceof Uint8Array ? new Uint8Array(p.data) : p.data], p.name, { type: p.mimeType || 'image/jpeg' })
      )
      await handleFiles(files)
      return
    } catch (e) {
      console.error('原生选图失败，回退 file input:', e)
    }
  }
  fileInputRef.value?.click()
}

async function handleFiles(files: File[]) {
  if (!files.length) return
  const sessionId = currentSessionId.value
  if (!sessionId) {
    message.error(t('extra.opFail'))
    return
  }

  uploading.value = true
  try {
    const { ok, error } = await groupMetaStore.uploadAlbumImages(
      sessionId,
      files,
      selectedAlbum.value || DEFAULT_GROUP_ALBUM_NAME_ZH
    )
    if (ok > 0) {
      tab.value = 'feed'
      message.success(t('extra.albumUploaded', { n: ok }))
    } else {
      message.error(error || t('extra.opFail'))
    }
  } catch (err) {
    const msg =
      axios.isAxiosError(err) && (err.response?.data as { message?: string } | undefined)?.message
    message.error(msg || t('extra.opFail'))
  } finally {
    uploading.value = false
  }
}

function onAlbumPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  void handleFiles(files)
}

function openFolder(name: string) {
  selectedAlbum.value = name
  tab.value = 'feed'
}

function previewImage(item: { id: string; url: string; name: string }) {
  const sessionId = currentSessionId.value
  if (!sessionId) return
  openOverlay('file-preview', {
    filePreview: {
      fileName: item.name,
      fileUrl: item.url,
      isImage: true,
      conversationId: sessionId,
      assetId: item.id
    }
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupAlbumOpen" class="modal-root" @click.self="close">
      <div class="album-window" @click.stop>
        <header class="lx-modal-win-head">
          <h2>{{ t('extra.groupAlbumTitle', { name: currentSession?.name || t('extra.groupChat') }) }}</h2>
          <ModalWinHeadActions :close-disabled="uploading" @close="close" />
        </header>
        <div class="tabs-row">
          <button type="button" class="tab" :class="{ 'is-active': tab === 'feed' }" @click="tab = 'feed'">
            {{ t('extra.groupFeed') }}
          </button>
          <button type="button" class="tab" :class="{ 'is-active': tab === 'albums' }" @click="tab = 'albums'">
            {{ t('extra.album') }}
          </button>
          <button type="button" class="tab" :class="{ 'is-active': tab === 'me' }" @click="tab = 'me'">
            {{ t('extra.relatedToMe') }}
          </button>
          <div class="tabs-actions">
            <LxButton variant="link-muted" :disabled="uploading" @click="openCreateAlbum">
              {{ t('extra.createAlbum') }}
            </LxButton>
            <LxButton variant="primary-sm" :disabled="uploading" @click="pickAndUpload">
              {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
            </LxButton>
            <input
              ref="fileInputRef"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp"
              multiple
              class="hidden-input"
              @change="onAlbumPicked"
            />
          </div>
        </div>

        <!-- 创建相册表单 -->
        <div v-if="createOpen" class="create-bar">
          <input
            v-model="newAlbumName"
            type="text"
            class="create-input"
            maxlength="32"
            :placeholder="t('extra.albumNamePh')"
            @keydown.enter.prevent="confirmCreateAlbum"
          />
          <LxButton variant="primary-sm" @click="confirmCreateAlbum">{{ t('common.confirm') }}</LxButton>
          <LxButton variant="link-muted" @click="createOpen = false">{{ t('common.cancel') }}</LxButton>
        </div>

        <p v-if="tab === 'feed'" class="album-hint">
          {{ t('extra.currentAlbum', { name: selectedAlbum }) }}
        </p>

        <!-- 相册文件夹 -->
        <div v-if="tab === 'albums'" class="folder-grid">
          <button
            v-for="folder in albumFolders"
            :key="folder.name"
            type="button"
            class="folder-card"
            @click="openFolder(folder.name)"
          >
            <div class="folder-cover">
              <GroupAlbumAuthImage
                v-if="folder.coverAssetId && currentSessionId"
                :conversation-id="currentSessionId"
                :asset-id="folder.coverAssetId"
                :fallback-url="folder.coverUrl"
                :alt="folder.name"
              />
              <img v-else-if="folder.coverUrl" :src="folder.coverUrl" :alt="folder.name" />
              <span v-else class="folder-empty">📁</span>
            </div>
            <span class="folder-name">{{ folder.name }}</span>
            <span class="folder-count">{{ t('extra.albumPhotoCount', { n: folder.count }) }}</span>
          </button>
        </div>

        <!-- 图片流 -->
        <div v-else-if="albumItems.length" class="album-grid">
          <button
            v-for="item in albumItems"
            :key="item.id"
            type="button"
            class="album-thumb"
            @click="previewImage(item)"
          >
            <GroupAlbumAuthImage
              v-if="currentSessionId"
              :conversation-id="currentSessionId"
              :asset-id="item.id"
              :fallback-url="item.url"
              :alt="item.name"
            />
            <span class="thumb-meta">{{ item.user }} · {{ item.time }}</span>
          </button>
        </div>
        <div v-else class="empty-area">
          <div class="empty-ico">🖼</div>
          <p>{{ uploading ? t('extra.uploading') : t('extra.uploadPhotosHint') }}</p>
          <LxButton variant="primary-lg" :disabled="uploading" @click="pickAndUpload">
            {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
          </LxButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-dialog);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-3xl);
}

.album-window {
  width: min(680px, 96vw);
  height: min(480px, 85vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
}

.tabs-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-3xl);
  padding: 0 var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
  flex-wrap: wrap;
}

.tab {
  border: none;
  background: none;
  padding: var(--lx-space-lg) 0;
  font-size: var(--lx-font);
  color: var(--lx-text-secondary);
  cursor: pointer;
  position: relative;
}

.tab.is-active {
  color: var(--lx-accent);
  font-weight: 600;
}

.tab.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--lx-accent);
}

.tabs-actions {
  margin-left: auto;
  display: flex;
  gap: var(--lx-space-md);
  align-items: center;
  padding: var(--lx-space) 0;
}

.hidden-input {
  display: none;
}

.create-bar {
  display: flex;
  gap: var(--lx-space);
  align-items: center;
  padding: var(--lx-space-md) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.create-input {
  flex: 1;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 var(--lx-space-md);
  font-size: var(--lx-font-md);
  outline: none;
  background: var(--lx-bg-panel);
  color: var(--lx-text-body);
}

.album-hint {
  margin: 0;
  padding: var(--lx-space) var(--lx-space-2xl) 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.folder-grid {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-2xl) var(--lx-space-2xl);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: var(--lx-space-lg);
  align-content: start;
}

.folder-card {
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.folder-cover {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-panel);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.folder-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.folder-empty {
  font-size: var(--lx-font-7xl);
  opacity: 0.45;
}

.folder-name {
  display: block;
  margin-top: var(--lx-space-sm);
  font-size: var(--lx-font-md);
  color: var(--lx-text-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.folder-count {
  display: block;
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
}

.album-grid {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-2xl) var(--lx-space-2xl);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: var(--lx-space-md);
  align-content: start;
}

.album-thumb {
  border: none;
  padding: 0;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  overflow: hidden;
  cursor: pointer;
  text-align: left;
}

.album-thumb img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  display: block;
}

.thumb-meta {
  display: block;
  font-size: var(--lx-font-2xs);
  color: var(--lx-text-muted);
  padding: var(--lx-space-xs) var(--lx-space-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-muted);
  font-size: var(--lx-font);
}

.empty-ico {
  font-size: var(--lx-font-7xl);
  opacity: 0.35;
  margin-bottom: var(--lx-space-2xl);
}
</style>
