<script setup lang="ts">
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
const selectedAlbum = ref('默认相册')
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
    list = list.filter(i => (i.albumName || '默认相册') === selectedAlbum.value)
  }
  return list
})

watch(groupAlbumOpen, open => {
  if (open) {
    tab.value = 'feed'
    selectedAlbum.value = '默认相册'
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
        p => new File([p.data], p.name, { type: p.mimeType || 'image/jpeg' })
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
      selectedAlbum.value || '默认相册'
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

function previewImage(item: { url: string; name: string }) {
  openOverlay('file-preview', {
    filePreview: {
      fileName: item.name,
      fileUrl: item.url,
      isImage: true
    }
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupAlbumOpen" class="modal-root" @click.self="close">
      <div class="album-window" @click.stop>
        <header class="win-head">
          <h2>{{ t('extra.groupAlbumTitle', { name: currentSession?.name || t('extra.groupChat') }) }}</h2>
          <button type="button" class="close-x" :disabled="uploading" @click="close">×</button>
        </header>
        <div class="tabs-row">
          <button type="button" class="tab" :class="{ active: tab === 'feed' }" @click="tab = 'feed'">
            {{ t('extra.groupFeed') }}
          </button>
          <button type="button" class="tab" :class="{ active: tab === 'albums' }" @click="tab = 'albums'">
            {{ t('extra.album') }}
          </button>
          <button type="button" class="tab" :class="{ active: tab === 'me' }" @click="tab = 'me'">
            {{ t('extra.relatedToMe') }}
          </button>
          <div class="tabs-actions">
            <button type="button" class="link-btn" :disabled="uploading" @click="openCreateAlbum">
              {{ t('extra.createAlbum') }}
            </button>
            <button type="button" class="primary-sm" :disabled="uploading" @click="pickAndUpload">
              {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
            </button>
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
          <button type="button" class="primary-sm" @click="confirmCreateAlbum">{{ t('common.confirm') }}</button>
          <button type="button" class="link-btn" @click="createOpen = false">{{ t('common.cancel') }}</button>
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
              <img v-if="folder.coverUrl" :src="folder.coverUrl" :alt="folder.name" />
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
            <img :src="item.url" :alt="item.name" />
            <span class="thumb-meta">{{ item.user }} · {{ item.time }}</span>
          </button>
        </div>
        <div v-else class="empty-area">
          <div class="empty-ico">🖼</div>
          <p>{{ uploading ? t('extra.uploading') : t('extra.uploadPhotosHint') }}</p>
          <button type="button" class="primary-lg" :disabled="uploading" @click="pickAndUpload">
            {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2200;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
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

.win-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--lx-border-light);
}

.win-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.close-x {
  border: none;
  background: none;
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
}

.close-x:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tabs-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 18px;
  border-bottom: 1px solid var(--lx-border-light);
  flex-wrap: wrap;
}

.tab {
  border: none;
  background: none;
  padding: 12px 0;
  font-size: 14px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  position: relative;
}

.tab.active {
  color: var(--lx-accent);
  font-weight: 600;
}

.tab.active::after {
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
  gap: 10px;
  align-items: center;
  padding: 8px 0;
}

.hidden-input {
  display: none;
}

.link-btn {
  border: none;
  background: none;
  color: var(--lx-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.link-btn:disabled,
.primary-sm:disabled,
.primary-lg:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.primary-sm {
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 12px;
  cursor: pointer;
}

.create-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 18px;
  border-bottom: 1px solid var(--lx-border-light);
}

.create-input {
  flex: 1;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 10px;
  font-size: 13px;
  outline: none;
  background: var(--lx-bg-panel);
  color: var(--lx-text-body);
}

.album-hint {
  margin: 0;
  padding: 8px 18px 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.folder-grid {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
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
  font-size: 36px;
  opacity: 0.45;
}

.folder-name {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  color: var(--lx-text-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.folder-count {
  display: block;
  font-size: 11px;
  color: var(--lx-text-muted);
}

.album-grid {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
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
  font-size: 10px;
  color: var(--lx-text-muted);
  padding: 4px 6px;
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
  font-size: 14px;
}

.empty-ico {
  font-size: 64px;
  opacity: 0.35;
  margin-bottom: 16px;
}

.primary-lg {
  margin-top: 20px;
  height: 36px;
  padding: 0 24px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 14px;
  cursor: pointer;
}
</style>
