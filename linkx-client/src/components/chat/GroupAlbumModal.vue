<script setup lang="ts">
/**
 * 群相册弹窗（群聊顶栏「应用」→「群相册」）。
 * 上传：POST /group/{conversationId}/assets/upload?type=image
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
  if (open) {
    tab.value = 'feed'
    const id = currentSessionId.value
    if (id) void groupMetaStore.fetchAlbum(id)
  }
})

function close() {
  if (uploading.value) return
  closeGroupAlbum()
}

async function handleFiles(files: FileList | File[] | null) {
  const list = files ? Array.from(files) : []
  if (!list.length) return

  const sessionId = currentSessionId.value
  if (!sessionId) {
    message.error(t('extra.opFail'))
    return
  }

  uploading.value = true
  try {
    const { ok, error } = await groupMetaStore.uploadAlbumImages(sessionId, list)
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
  const files = input.files
  input.value = ''
  void handleFiles(files)
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
            <!--
              关键透明 file input 盖住按钮：用户实际点到的是 input，
              避免 Electron 下 label.for / input.click() 无响应。
            -->
            <span class="file-btn link-btn" :class="{ disabled: uploading }">
              <input
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp"
                multiple
                class="file-overlay"
                :disabled="uploading"
                @change="onAlbumPicked"
              />
              {{ t('extra.createAlbum') }}
            </span>
            <span class="file-btn primary-sm" :class="{ disabled: uploading }">
              <input
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp"
                multiple
                class="file-overlay"
                :disabled="uploading"
                @change="onAlbumPicked"
              />
              {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
            </span>
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
          <span class="file-btn primary-lg" :class="{ disabled: uploading }">
            <input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp"
              multiple
              class="file-overlay"
              :disabled="uploading"
              @change="onAlbumPicked"
            />
            {{ uploading ? t('extra.uploading') : t('extra.uploadToAlbum') }}
          </span>
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

.file-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
  user-select: none;
}

.file-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  font-size: 0;
}

.file-btn.disabled {
  opacity: 0.55;
  pointer-events: none;
  cursor: not-allowed;
}

.link-btn {
  border: none;
  background: none;
  color: var(--lx-text-secondary);
  font-size: 13px;
  min-height: 30px;
  padding: 0 4px;
}

.primary-sm {
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 12px;
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
}
</style>
