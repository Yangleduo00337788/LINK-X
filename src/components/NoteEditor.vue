<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  RemoveOutline,
  CloseOutline,
  SquareOutline,
  CopyOutline,
  ImageOutline,
  FolderOpenOutline,
  MicOutline,
  LocationOutline,
  ListOutline,
  CheckboxOutline,
  TextOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNoteStore } from '../stores/note'

const message = useMessage()
const noteStore = useNoteStore()
const { title, content } = storeToRefs(noteStore)

const isMaximized = ref(false)
let saveTimer: ReturnType<typeof setTimeout> | null = null

function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => noteStore.save(), 400)
}

watch([title, content], scheduleSave)

function minimizeWindow() {
  window.electronAPI?.minimize()
}

function toggleMaximize() {
  if (window.electronAPI) {
    window.electronAPI.maximize()
    window.electronAPI.isMaximized().then(res => {
      isMaximized.value = res
    })
  }
}

function closeWindow() {
  noteStore.save()
  if (window.electronAPI) {
    window.electronAPI.close()
  } else {
    window.close()
  }
}

const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

function insertImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  content.value += `\n[图片: ${file.name}]\n`
  message.success('图片已插入笔记')
  noteStore.save()
}

function insertFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  content.value += `\n[附件: ${file.name}]\n`
  message.success('附件已插入笔记')
  noteStore.save()
}

function handleToolClick(toolName: string) {
  if (toolName === '插入图片') imageInputRef.value?.click()
  else if (toolName === '插入附件') fileInputRef.value?.click()
  else message.info(`「${toolName}」将在后续版本支持`)
}

let cleanupMaximizedListener: (() => void) | undefined

onMounted(() => {
  noteStore.load()
  if (window.electronAPI?.onMaximizedChange) {
    cleanupMaximizedListener = window.electronAPI.onMaximizedChange((maximized) => {
      isMaximized.value = maximized
    })
  }
})

onUnmounted(() => {
  if (saveTimer) clearTimeout(saveTimer)
  cleanupMaximizedListener?.()
})
</script>

<template>
  <div class="note-editor standalone-window">
    <div class="header drag-area">
      <div class="header-left">
        <span class="title">笔记</span>
      </div>
      <div class="header-right no-drag">
        <div class="action-btn" title="最小化" @click="minimizeWindow">
          <n-icon :component="RemoveOutline" size="18" />
        </div>
        <div class="action-btn" title="最大化" @click="toggleMaximize">
          <n-icon :component="isMaximized ? CopyOutline : SquareOutline" size="14" />
        </div>
        <div class="action-btn close" title="关闭" @click="closeWindow">
          <n-icon :component="CloseOutline" size="18" />
        </div>
      </div>
    </div>

    <div class="editor-body">
      <input v-model="title" class="title-input" placeholder="标题" />
      <textarea v-model="content" class="content-input" placeholder="开始记录…" />
    </div>

    <div class="toolbar no-drag">
      <input ref="imageInputRef" type="file" accept="image/*" hidden @change="insertImage" />
      <input ref="fileInputRef" type="file" hidden @change="insertFile" />
      <button type="button" class="tool-btn" title="图片" @click="handleToolClick('插入图片')">
        <n-icon :component="ImageOutline" size="18" />
      </button>
      <button type="button" class="tool-btn" title="附件" @click="handleToolClick('插入附件')">
        <n-icon :component="FolderOpenOutline" size="18" />
      </button>
      <button type="button" class="tool-btn" title="语音" @click="handleToolClick('语音输入')">
        <n-icon :component="MicOutline" size="18" />
      </button>
      <button type="button" class="tool-btn" title="位置" @click="handleToolClick('位置')">
        <n-icon :component="LocationOutline" size="18" />
      </button>
      <button type="button" class="tool-btn" title="列表" @click="handleToolClick('列表')">
        <n-icon :component="ListOutline" size="18" />
      </button>
      <button type="button" class="tool-btn" title="待办" @click="handleToolClick('待办')">
        <n-icon :component="CheckboxOutline" size="18" />
      </button>
      <button type="button" class="tool-btn" title="正文" @click="handleToolClick('正文样式')">
        <n-icon :component="TextOutline" size="18" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.note-editor {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
}

.header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
}

.header-left .title {
  font-size: 14px;
  font-weight: 500;
}

.header-right {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--lx-radius);
  cursor: pointer;
  color: var(--lx-text-secondary);
}

.action-btn:hover {
  background: var(--lx-bg-hover);
}

.action-btn.close:hover {
  background: #ff4d4f;
  color: #fff;
}

.editor-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 24px;
  gap: 12px;
  overflow: hidden;
}

.title-input {
  border: none;
  outline: none;
  font-size: 22px;
  font-weight: 600;
  background: transparent;
  color: var(--lx-text-body);
}

.content-input {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.6;
  background: transparent;
  color: var(--lx-text-body);
}

.toolbar {
  display: flex;
  gap: 4px;
  padding: 8px 16px;
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
}

.tool-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  cursor: pointer;
  color: var(--lx-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.tool-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-accent);
}
</style>
