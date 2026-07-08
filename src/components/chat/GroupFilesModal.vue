<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useFilesStore } from '../../stores/files'
import { useMessage } from 'naive-ui'
import { formatFileSize } from '../../utils/file'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const filesStore = useFilesStore()
const { groupFilesOpen } = storeToRefs(chatModalsStore)
const { closeGroupFiles } = chatModalsStore
const { currentSession } = storeToRefs(appStore)

const search = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)

const fileGroups = [
  {
    month: '2026年7月',
    files: [
      { name: 'sub2api.2026-07-04_08-04-03.json', size: '58.4 KB', dl: 12, user: '蓬蒿人', date: '07/04' },
      { name: 'Cursor 账号3万+.txt', size: '15.7 MB', dl: 120, user: '打工人', date: '07/01' }
    ]
  }
]

function close() {
  closeGroupFiles()
}

function triggerUpload() {
  uploadInputRef.value?.click()
}

function onUploadPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  filesStore.addFromChat(file.name, formatFileSize(file.size), currentSession.value?.name || '群聊')
  message.success(`已上传「${file.name}」到群文件`)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupFilesOpen" class="modal-root" @click.self="close">
      <div class="files-window" @click.stop>
        <header class="win-head">
          <h2>群文件 - {{ currentSession?.name || '群聊' }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <div class="search-row">
          <input v-model="search" type="text" class="search-field" placeholder="搜索" />
        </div>
        <div class="file-scroll">
          <section v-for="g in fileGroups" :key="g.month" class="month-block">
            <h3 class="month-title">{{ g.month }}</h3>
            <div v-for="(f, i) in g.files" :key="i" class="file-row">
              <div class="file-ico">📄</div>
              <div class="file-main">
                <div class="file-name">{{ f.name }}</div>
                <div class="file-meta">
                  <span>{{ f.size }}</span>
                  <span>{{ f.dl }}次下载</span>
                  <span>{{ f.user }}</span>
                  <span>{{ f.date }}</span>
                </div>
              </div>
            </div>
          </section>
        </div>
        <footer class="win-foot">
          <span>共 {{ fileGroups[0].files.length }} 个文件</span>
          <input ref="uploadInputRef" type="file" hidden @change="onUploadPicked" />
          <button type="button" class="upload-btn" @click="triggerUpload">上传文件</button>
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
