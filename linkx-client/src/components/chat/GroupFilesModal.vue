<script setup lang="ts">
// Vue 响应式 API 与计算属性
import { ref, computed } from 'vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'
// 群元数据 Store（文件、公告等）
import { useGroupMetaStore } from '../../stores/groupMeta'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../stores/overlay'
// Naive UI 全局消息提示
import { useMessage } from 'naive-ui'
// 文件大小格式化工具
import { formatFileSize } from '../../utils/file'
import { useI18n } from '../../i18n'

const message = useMessage()
const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const overlayStore = useOverlayStore()
const { groupFilesOpen } = storeToRefs(chatModalsStore)
const { closeGroupFiles } = chatModalsStore
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)
const { open: openOverlay } = overlayStore

const search = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)

// 当前群聊的全部文件列表
const allFiles = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.filesFor(id)
})

// 按搜索词过滤后的文件列表
const filteredFiles = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return allFiles.value
  return allFiles.value.filter(
    f => f.name.toLowerCase().includes(q) || f.user.toLowerCase().includes(q)
  )
})

// 将文件按月份分组展示
function formatMonth(dateStr?: string): string {
  if (!dateStr) return t('extra.unknownTime')
  try {
    if (dateStr.includes('月') || dateStr.includes('/')) {
      return dateStr.length > 7 ? dateStr.slice(0, 7) : dateStr
    }
    const date = new Date(dateStr)
    if (Number.isNaN(date.getTime())) return dateStr
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    return t('calendar.yearMonth', { y, m: Number(m) })
  } catch {
    return dateStr
  }
}

const fileGroups = computed(() => {
  const map = new Map<string, typeof filteredFiles.value>()
  for (const f of filteredFiles.value) {
    const month = formatMonth(f.date)
    if (!map.has(month)) map.set(month, [])
    map.get(month)!.push(f)
  }
  return [...map.entries()].map(([month, files]) => ({ month, files }))
})

// 关闭群文件弹窗
function close() {
  closeGroupFiles()
}

// 触发隐藏 input 选择文件
function triggerUpload() {
  uploadInputRef.value?.click()
}

// 打开文件预览覆盖层
function openFile(f: { name: string; size: string; fileUrl?: string }) {
  openOverlay('file-preview', {
    filePreview: {
      fileName: f.name,
      fileSize: f.size,
      fileUrl: f.fileUrl,
      isImage: /\.(png|jpe?g|gif|webp)$/i.test(f.name)
    }
  })
}

// 处理用户选择的本地文件并上传到群文件
function onUploadPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !currentSessionId.value) return

  // 生成本地预览 URL 并格式化大小
  const fileUrl = URL.createObjectURL(file)
  const size = formatFileSize(file.size)
  const user = userProfile.value.nickname

  groupMetaStore.addFile(currentSessionId.value, {
    name: file.name,
    size,
    user,
    fileUrl
  })
  message.success(t('extra.fileUploaded', { name: file.name }))
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupFilesOpen" class="modal-root" @click.self="close">
      <div class="files-window" @click.stop>
        <header class="win-head">
          <h2>{{ t('extra.groupFilesTitle', { name: currentSession?.name || t('extra.groupChat') }) }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <div class="search-row">
          <input v-model="search" type="text" class="search-field" :placeholder="t('common.search')" />
        </div>
        <div class="file-scroll">
          <section v-for="g in fileGroups" :key="g.month" class="month-block">
            <h3 class="month-title">{{ g.month }}</h3>
            <div
              v-for="f in g.files"
              :key="f.id"
              class="file-row"
              @click="openFile(f)"
            >
              <div class="file-ico">📄</div>
              <div class="file-main">
                <div class="file-name">{{ f.name }}</div>
                <div class="file-meta">
                  <span>{{ f.size }}</span>
                  <span>{{ t('extra.downloadCount', { n: f.downloads }) }}</span>
                  <span>{{ f.user }}</span>
                  <span>{{ f.date }}</span>
                </div>
              </div>
            </div>
          </section>
          <p v-if="!filteredFiles.length" class="empty">{{ t('extra.noGroupFiles') }}</p>
        </div>
        <footer class="win-foot">
          <span>{{ t('extra.fileCount', { n: filteredFiles.length }) }}</span>
          <input ref="uploadInputRef" type="file" hidden @change="onUploadPicked" />
          <button type="button" class="upload-btn" @click="triggerUpload">{{ t('extra.uploadFile') }}</button>
        </footer>
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
  padding: 24px;
}

.files-window {
  width: min(640px, 92vw);
  height: min(520px, 85vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.win-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--lx-border-light);
}

.win-head h2 {
  margin: 0;
  font-size: 16px;
  color: var(--lx-text-body);
}

.close-x {
  border: none;
  background: none;
  font-size: 22px;
  cursor: pointer;
  color: var(--lx-text-muted);
}

.search-row {
  padding: 12px 20px;
}

.search-field {
  width: 100%;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 12px;
  background: var(--lx-bg-card);
  color: var(--lx-text);
}

.file-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px;
}

.month-title {
  font-size: 13px;
  color: var(--lx-text-muted);
  margin: 12px 0 8px;
}

.file-row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--lx-border-light);
  cursor: pointer;
}

.file-row:hover {
  background: var(--lx-bg-panel);
}

.file-name {
  font-size: 14px;
  color: var(--lx-text-body);
}

.file-meta {
  font-size: 12px;
  color: var(--lx-text-muted);
  display: flex;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 32px;
}

.win-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid var(--lx-border-light);
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.upload-btn {
  height: 32px;
  padding: 0 16px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  cursor: pointer;
}
</style>
