<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
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
const albumInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const albumInputId = 'group-album-upload-input'

const albumItems = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  const list = groupMetaStore.albumFor(id)
  if (tab.value === 'me') {
    const myId = userProfile.value.userId
    const myName = userProfile.value.nickname
    return list.filter(i => (myId && i.uploaderId === myId) || (!!myName && i.user === myName))
  }
  return list
})

watch(groupAlbumOpen, open => {
  if (open) tab.value = 'feed'
})

function close() {
  closeGroupAlbum()
}

/** 打开系统文件选择器（label 优先；创建相册走程序化兜底） */
function openFilePicker() {
  const input = albumInputRef.value
  if (!input) {
    message.error(t('extra.opFail'))
    return
  }
  input.value = ''
  input.click()
}

function createAlbum() {
  // 后端暂无多相册，创建即进入默认相册并选图上传
  tab.value = 'albums'
  void nextTick(() => openFilePicker())
}

async function onAlbumPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  input.value = ''
  if (!files?.length || !currentSessionId.value) return

  uploading.value = true
  try {
    const { ok, error } = await groupMetaStore.uploadAlbumImages(
      currentSessionId.value,
      Array.from(files)
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
          <button type="button" class="close-x" @click="close">×</button>
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
            <button type="button" class="link-btn" :disabled="uploading" @click="createAlbum">
              {{ t('extra.createAlbum') }}
            </button>
            <!-- 用 label 关联 input，避免 Electron 下 programmatic click 无响应 -->
            <input
              :id="albumInputId"
              ref="albumInputRef"
              type="file"
              accept="image/*"
              multiple
              class="visually-hidden"
              :disabled="uploading"
              @change="onAlbumPicked"
            />
            <label :for="albumInputId" class="primary-sm" :class="{ disabled: uploading }">
              {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
            </label>
          </div>
        </div>
        <div v-if="albumItems.length" class="album-grid">
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
          <label :for="albumInputId" class="primary-lg" :class="{ disabled: uploading }">
            {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
          </label>
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

.link-btn {
  border: none;
  background: none;
  color: var(--lx-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.link-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.primary-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 12px;
  cursor: pointer;
}

.primary-sm.disabled,
.primary-lg.disabled {
  opacity: 0.6;
  pointer-events: none;
  cursor: not-allowed;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
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
