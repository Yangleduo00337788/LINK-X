<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
// Vue 响应式 API 与计算属性
import { ref, computed, watch } from 'vue'
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
import { useI18n } from '../../i18n'
import { LxButton } from '../ui'

const message = useMessage()
const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const overlayStore = useOverlayStore()
const { groupFilesOpen } = storeToRefs(chatModalsStore)
const { closeGroupFiles } = chatModalsStore
const { currentSession, currentSessionId } = storeToRefs(appStore)
const { open: openOverlay } = overlayStore

const search = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)

// 当前群聊的全部文件列表
const allFiles = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.filesFor(id)
})

watch(
  [groupFilesOpen, currentSessionId],
  ([open, id]) => {
    if (!open || !id) return
    void groupMetaStore.fetchFiles(id)
  },
  { immediate: true }
)

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
async function onUploadPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !currentSessionId.value) return

  try {
    const ok = await groupMetaStore.uploadFile(currentSessionId.value, file)
    if (ok) {
      message.success(t('extra.fileUploaded', { name: file.name }))
    } else {
      message.error(t('extra.opFail'))
    }
  } catch {
    message.error(t('extra.opFail'))
  }
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
          <LxButton variant="upload" @click="triggerUpload">{{ t('extra.uploadFile') }}</LxButton>
        </footer>
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
  padding: var(--lx-space-4xl);
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
  padding: var(--lx-space-2xl) var(--lx-space-3xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.win-head h2 {
  margin: 0;
  font-size: var(--lx-font-xl);
  color: var(--lx-text-body);
}

.close-x {
  border: none;
  background: none;
  font-size: var(--lx-font-5xl);
  cursor: pointer;
  color: var(--lx-text-muted);
}

.search-row {
  padding: var(--lx-space-lg) var(--lx-space-3xl);
}

.search-field {
  width: 100%;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 var(--lx-space-lg);
  background: var(--lx-bg-card);
  color: var(--lx-text);
}

.file-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--lx-space-3xl);
}

.month-title {
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  margin: var(--lx-space-lg) 0 var(--lx-space);
}

.file-row {
  display: flex;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-md) 0;
  border-bottom: 1px solid var(--lx-border-light);
  cursor: pointer;
}

.file-row:hover {
  background: var(--lx-bg-panel);
}

.file-name {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
}

.file-meta {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  display: flex;
  gap: var(--lx-space);
  margin-top: var(--lx-space-xs);
  flex-wrap: wrap;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: var(--lx-space-5xl);
}

.win-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-lg) var(--lx-space-3xl);
  border-top: 1px solid var(--lx-border-light);
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
}

</style>
