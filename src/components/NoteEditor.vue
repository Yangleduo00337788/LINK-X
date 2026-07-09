<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { NIcon, NDropdown, useMessage } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import {
  MicOutline,
  FolderOpenOutline,
  ListOutline,
  CheckboxOutline,
  TextOutline,
  ArrowUndoOutline,
  ArrowRedoOutline,
  EllipsisHorizontalOutline,
  ReorderTwoOutline
} from '@vicons/ionicons5'
import PinIcon from './icons/PinIcon.vue'
import WindowControls from './WindowControls.vue'
import { storeToRefs } from 'pinia'
import { useNoteStore } from '../stores/note'
import { useAppStore } from '../stores/app'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'

const message = useMessage()
const noteStore = useNoteStore()
const appStore = useAppStore()
const { title, content } = storeToRefs(noteStore)
const { theme } = storeToRefs(appStore)

const contentRef = ref<HTMLTextAreaElement | null>(null)
const isPinned = ref(false)
const isRecordingNote = ref(false)

const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

let saveTimer: ReturnType<typeof setTimeout> | null = null
let historyTimer: ReturnType<typeof setTimeout> | null = null
let noteRecordStart = 0
let restoringHistory = false

const historyStack = ref<string[]>([''])
const historyIndex = ref(0)

const moreOptions: DropdownOption[] = [
  { label: '插入图片', key: 'image' },
  { label: '插入位置', key: 'location' },
  { label: '清空笔记', key: 'clear' }
]

function syncTitleFromContent() {
  const first = content.value.split('\n').find(line => line.trim())?.trim() ?? ''
  title.value = first.slice(0, 80) || '笔记'
}

function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    syncTitleFromContent()
    noteStore.save()
  }, 400)
}

function pushHistorySnapshot() {
  if (restoringHistory) return
  const val = content.value
  if (historyStack.value[historyIndex.value] === val) return
  historyStack.value = historyStack.value.slice(0, historyIndex.value + 1)
  historyStack.value.push(val)
  if (historyStack.value.length > 60) {
    historyStack.value.shift()
  } else {
    historyIndex.value += 1
  }
}

function scheduleHistory() {
  if (historyTimer) clearTimeout(historyTimer)
  historyTimer = setTimeout(pushHistorySnapshot, 600)
}

watch(content, () => {
  scheduleSave()
  scheduleHistory()
})

function undo() {
  if (historyIndex.value <= 0) return
  restoringHistory = true
  historyIndex.value -= 1
  content.value = historyStack.value[historyIndex.value] ?? ''
  restoringHistory = false
  noteStore.save()
}

function redo() {
  if (historyIndex.value >= historyStack.value.length - 1) return
  restoringHistory = true
  historyIndex.value += 1
  content.value = historyStack.value[historyIndex.value] ?? ''
  restoringHistory = false
  noteStore.save()
}

function wrapSelection(prefix: string, suffix = prefix) {
  const el = contentRef.value
  if (!el) {
    content.value += prefix + suffix
    return
  }
  const start = el.selectionStart ?? content.value.length
  const end = el.selectionEnd ?? start
  const selected = content.value.slice(start, end)
  const next = content.value.slice(0, start) + prefix + selected + suffix + content.value.slice(end)
  content.value = next
  pushHistorySnapshot()
  requestAnimationFrame(() => {
    el.focus()
    const cursor = start + prefix.length + selected.length + suffix.length
    el.setSelectionRange(selected ? cursor : start + prefix.length, selected ? cursor : start + prefix.length)
  })
}

function insertAtCursor(text: string) {
  const el = contentRef.value
  if (!el) {
    content.value += text
    pushHistorySnapshot()
    return
  }
  const start = el.selectionStart ?? content.value.length
  const end = el.selectionEnd ?? start
  content.value = content.value.slice(0, start) + text + content.value.slice(end)
  pushHistorySnapshot()
  requestAnimationFrame(() => {
    el.focus()
    const pos = start + text.length
    el.setSelectionRange(pos, pos)
  })
}

function insertBlock(text: string) {
  insertAtCursor((content.value && !content.value.endsWith('\n') ? '\n' : '') + text + '\n')
}

async function togglePin() {
  if (!window.electronAPI?.togglePin) return
  isPinned.value = await window.electronAPI.togglePin()
}

function insertImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  insertBlock(`[图片: ${file.name}]`)
  message.success('图片已插入')
}

function insertFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  insertBlock(`[附件: ${file.name}]`)
  message.success('附件已插入')
}

async function toggleNoteVoice() {
  if (!isRecordingNote.value) {
    try {
      await navigator.mediaDevices.getUserMedia({ audio: true })
      noteRecordStart = Date.now()
      isRecordingNote.value = true
      message.info('正在录音，再次点击结束')
    } catch {
      insertBlock('[语音备忘: 3"]')
      message.success('语音备忘已插入')
    }
    return
  }
  const sec = Math.max(1, Math.round((Date.now() - noteRecordStart) / 1000))
  isRecordingNote.value = false
  insertBlock(`[语音备忘: ${sec}"]`)
  message.success('语音备忘已插入')
}

function onMoreSelect(key: string) {
  if (key === 'image') imageInputRef.value?.click()
  else if (key === 'location') {
    insertBlock('[位置: 深圳市南山区]')
    message.success('位置已插入')
  } else if (key === 'clear') {
    content.value = ''
    title.value = '笔记'
    pushHistorySnapshot()
    message.success('笔记已清空')
  }
}

onMounted(async () => {
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  noteStore.load()
  historyStack.value = [content.value]
  historyIndex.value = 0
  if (window.electronAPI?.isPinned) {
    isPinned.value = await window.electronAPI.isPinned()
  }
})

watch(theme, t => {
  applyDocumentTheme(t)
  notifyElectronTheme(t)
})

onUnmounted(() => {
  syncTitleFromContent()
  noteStore.save()
  if (saveTimer) clearTimeout(saveTimer)
  if (historyTimer) clearTimeout(historyTimer)
})
</script>

<template>
  <div class="note-editor standalone-window">
    <header class="title-bar drag-area">
      <div class="bar-side bar-left no-drag">
        <button
          type="button"
          class="icon-btn pin"
          :class="{ active: isPinned }"
          title="置顶窗口"
          @click="togglePin"
        >
          <PinIcon :size="16" />
        </button>
      </div>
      <div class="bar-center">笔记</div>
      <div class="bar-side bar-right no-drag">
        <WindowControls />
      </div>
    </header>

    <div class="format-bar no-drag">
      <input ref="imageInputRef" type="file" accept="image/*" hidden @change="insertImage" />
      <input ref="fileInputRef" type="file" hidden @change="insertFile" />

      <button
        type="button"
        class="icon-btn"
        :class="{ recording: isRecordingNote }"
        title="语音输入"
        @click="toggleNoteVoice"
      >
        <n-icon :component="MicOutline" :size="18" />
      </button>
      <button type="button" class="icon-btn" title="附件" @click="fileInputRef?.click()">
        <n-icon :component="FolderOpenOutline" :size="18" />
      </button>

      <span class="v-sep" />

      <button type="button" class="text-btn" title="加粗" @click="wrapSelection('**')">B</button>
      <button type="button" class="icon-btn" title="标题" @click="insertBlock('## 小标题')">
        <n-icon :component="TextOutline" :size="17" />
      </button>
      <button type="button" class="text-btn underline" title="下划线" @click="wrapSelection('__')">U</button>
      <button type="button" class="text-btn italic" title="斜体" @click="wrapSelection('_')">I</button>

      <span class="v-sep" />

      <button type="button" class="icon-btn" title="分隔线" @click="insertBlock('---')">
        <n-icon :component="ReorderTwoOutline" :size="18" />
      </button>
      <button
        type="button"
        class="icon-btn"
        title="无序列表"
        @click="insertBlock('- 列表项 1\n- 列表项 2\n- 列表项 3')"
      >
        <n-icon :component="ListOutline" :size="18" />
      </button>
      <button
        type="button"
        class="icon-btn"
        title="有序列表"
        @click="insertBlock('1. 列表项 1\n2. 列表项 2\n3. 列表项 3')"
      >
        <span class="num-list">1.</span>
      </button>
      <button type="button" class="icon-btn" title="待办清单" @click="insertBlock('- [ ] 待办事项')">
        <n-icon :component="CheckboxOutline" :size="18" />
      </button>

      <span class="v-sep" />

      <button type="button" class="icon-btn" title="撤销" @click="undo">
        <n-icon :component="ArrowUndoOutline" :size="18" />
      </button>
      <button type="button" class="icon-btn" title="重做" @click="redo">
        <n-icon :component="ArrowRedoOutline" :size="18" />
      </button>

      <n-dropdown trigger="click" :options="moreOptions" @select="onMoreSelect">
        <button type="button" class="icon-btn" title="更多">
          <n-icon :component="EllipsisHorizontalOutline" :size="18" />
        </button>
      </n-dropdown>
    </div>

    <main class="editor-area">
      <textarea
        ref="contentRef"
        v-model="content"
        class="editor-input"
        placeholder="按住 Ctrl + Win 可以使用语音输入文字，记录的文字、图片等内容将自动保存。"
      />
    </main>
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
  border-radius: var(--lx-radius);
  overflow: hidden;
}

.title-bar {
  height: 40px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 4px 0 8px;
  flex-shrink: 0;
  -webkit-app-region: drag;
  user-select: none;
}

.bar-side {
  display: flex;
  align-items: center;
  min-width: 0;
}

.bar-left {
  justify-content: flex-start;
}

.bar-right {
  justify-content: flex-end;
}

.bar-center {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
  text-align: center;
  pointer-events: none;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.icon-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.icon-btn.active,
.icon-btn.recording {
  color: var(--lx-accent);
}

.icon-btn.recording {
  background: var(--lx-accent-soft);
}

.text-btn {
  min-width: 28px;
  height: 32px;
  padding: 0 6px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  color: var(--lx-text-secondary);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.text-btn.italic {
  font-style: italic;
  font-weight: 600;
}

.text-btn.underline {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.text-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.format-bar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 10px;
  flex-shrink: 0;
  overflow-x: auto;
  border-bottom: 1px solid var(--lx-border-light);
}

.v-sep {
  width: 1px;
  height: 18px;
  background: var(--lx-border-light);
  margin: 0 4px;
  flex-shrink: 0;
}

.num-list {
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  font-family: inherit;
}

.editor-area {
  flex: 1;
  min-height: 0;
  display: flex;
  background: var(--lx-bg-card);
}

.editor-input {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 14px 18px 20px;
  font-size: 14px;
  line-height: 1.65;
  background: transparent;
  color: var(--lx-text-body);
  font-family: inherit;
}

.editor-input::placeholder {
  color: var(--lx-text-muted);
  font-size: 13px;
}
</style>
