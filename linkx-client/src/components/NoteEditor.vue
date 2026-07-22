<script setup lang="ts">
/**
 * 笔记编辑器独立窗口
 */
import { ref, onMounted, onUnmounted, watch, computed, h } from 'vue'
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
  ReorderTwoOutline,
  EyeOutline,
  TrashOutline,
  AddOutline,
  CreateOutline,
  DocumentTextOutline,
  CloudUploadOutline
} from '@vicons/ionicons5'
import PinIcon from './icons/PinIcon.vue'
import { storeToRefs } from 'pinia'
import { useNoteStore } from '../stores/note'
import { useAppStore } from '../stores/app'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import * as noteApi from '../api/note'
import LocationPickerPage from './LocationPickerPage.vue'
import {
  VOICE_MAX_SECONDS,
  blobToVoiceFile,
  isVoiceDurationValid,
  pickVoiceMimeType
} from '../utils/voiceRecorder'

const message = useMessage()
const noteStore = useNoteStore()
const appStore = useAppStore()
const { title, content, notes, currentNoteId, saving } = storeToRefs(noteStore)
const { theme } = storeToRefs(appStore)

const contentRef = ref<HTMLTextAreaElement | null>(null)
const isPinned = ref(false)
const isRecordingNote = ref(false)
const showPreview = ref(false)
const showNoteList = ref(false)
const showLocationPicker = ref(false)
const mediaUrlCache = ref<Record<string, string>>({})
const uploadingMedia = ref(false)

const compiledMarkdown = computed(() => {
  let md = content.value
  for (const [key, url] of Object.entries(mediaUrlCache.value)) {
    md = md.split(`(lx-media:${key})`).join(`(${url})`)
  }
  const rawHtml = marked(md) as string
  return DOMPurify.sanitize(rawHtml, { ADD_TAGS: ['audio'], ADD_ATTR: ['controls', 'src', 'preload'] })
})

watch(
  content,
  async (val) => {
    const keys = [...val.matchAll(/\(lx-media:([^)]+)\)/g)].map(m => m[1])
    for (const key of keys) {
      if (!key || mediaUrlCache.value[key]) continue
      try {
        const res = await noteApi.resolveNoteMediaUrl(key)
        if (res.code === 200 && res.data) {
          mediaUrlCache.value = { ...mediaUrlCache.value, [key]: res.data }
        }
      } catch {
        /* ignore */
      }
    }
  },
  { immediate: true }
)

const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

let saveTimer: ReturnType<typeof setTimeout> | null = null
let historyTimer: ReturnType<typeof setTimeout> | null = null
let noteRecordStart = 0
let restoringHistory = false
let noteMediaRecorder: MediaRecorder | null = null
let noteMediaStream: MediaStream | null = null
let noteVoiceChunks: BlobPart[] = []
let noteVoiceMaxTimer: number | null = null

const historyStack = ref<string[]>([''])
const historyIndex = ref(0)

const moreOptions: DropdownOption[] = [
  { label: '插入图片', key: 'image' },
  { label: '插入位置', key: 'location' },
  { type: 'divider', key: 'd1' },
  { label: '清空笔记', key: 'clear' }
]

const noteListOptions = computed<DropdownOption[]>(() => [
  { label: '新建笔记', key: 'new' },
  ...notes.value.slice(0, 10).map(n => ({
    label: n.title || '无标题',
    key: n.id
  }))
])

function syncTitleFromContent() {
  const first = content.value.split('\n').find(line => line.trim())?.trim() ?? ''
  title.value = first.slice(0, 80) || '无标题'
}

function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    syncTitleFromContent()
    void noteStore.save()
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
  void noteStore.save()
}

function redo() {
  if (historyIndex.value >= historyStack.value.length - 1) return
  restoringHistory = true
  historyIndex.value += 1
  content.value = historyStack.value[historyIndex.value] ?? ''
  restoringHistory = false
  void noteStore.save()
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

async function insertImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploadingMedia.value = true
  try {
    const res = await noteApi.uploadNoteFile(file)
    if (res.code !== 200 || !res.data?.fileKey) {
      throw new Error(res.message || '上传失败')
    }
    if (res.data.url) {
      mediaUrlCache.value = { ...mediaUrlCache.value, [res.data.fileKey]: res.data.url }
    }
    insertBlock(`![${file.name}](lx-media:${res.data.fileKey})`)
    message.success('图片已插入')
  } catch (err) {
    message.error(err instanceof Error ? err.message : '图片上传失败')
  } finally {
    uploadingMedia.value = false
  }
}

async function insertFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploadingMedia.value = true
  try {
    const res = await noteApi.uploadNoteFile(file)
    if (res.code !== 200 || !res.data?.fileKey) {
      throw new Error(res.message || '上传失败')
    }
    if (res.data.url) {
      mediaUrlCache.value = { ...mediaUrlCache.value, [res.data.fileKey]: res.data.url }
    }
    insertBlock(`[附件: ${file.name}](lx-media:${res.data.fileKey})`)
    message.success('附件已插入')
  } catch (err) {
    message.error(err instanceof Error ? err.message : '附件上传失败')
  } finally {
    uploadingMedia.value = false
  }
}

function resetNoteVoice() {
  if (noteVoiceMaxTimer != null) {
    window.clearTimeout(noteVoiceMaxTimer)
    noteVoiceMaxTimer = null
  }
  noteMediaStream?.getTracks().forEach(t => t.stop())
  noteMediaStream = null
  noteMediaRecorder = null
  noteVoiceChunks = []
  isRecordingNote.value = false
  noteRecordStart = 0
}

async function finishNoteVoice(cancel: boolean) {
  const recorder = noteMediaRecorder
  if (!recorder || recorder.state === 'inactive') {
    resetNoteVoice()
    return
  }
  const mimeType = recorder.mimeType || pickVoiceMimeType() || 'audio/webm'
  const startedAt = noteRecordStart
  const blob = await new Promise<Blob>((resolve, reject) => {
    recorder.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) noteVoiceChunks.push(ev.data)
    }
    recorder.onerror = () => reject(new Error('record_error'))
    recorder.onstop = () => resolve(new Blob(noteVoiceChunks, { type: mimeType }))
    try {
      recorder.stop()
    } catch (e) {
      reject(e)
    }
  }).catch(() => null)

  const durationSec = Math.round((Date.now() - startedAt) / 1000)
  resetNoteVoice()
  if (cancel || !blob || blob.size === 0) return
  if (!isVoiceDurationValid(durationSec)) {
    message.warning('录音时间太短')
    return
  }

  const file = blobToVoiceFile(blob, mimeType, durationSec)
  uploadingMedia.value = true
  try {
    const res = await noteApi.uploadNoteFile(file)
    if (res.code !== 200 || !res.data?.fileKey) {
      throw new Error(res.message || '上传失败')
    }
    if (res.data.url) {
      mediaUrlCache.value = { ...mediaUrlCache.value, [res.data.fileKey]: res.data.url }
    }
    insertBlock(`[语音 ${durationSec}"](lx-media:${res.data.fileKey})`)
    message.success('语音已插入')
  } catch (err) {
    message.error(err instanceof Error ? err.message : '语音上传失败')
  } finally {
    uploadingMedia.value = false
  }
}

async function toggleNoteVoice() {
  if (uploadingMedia.value) return
  if (isRecordingNote.value) {
    await finishNoteVoice(false)
    return
  }
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    message.warning('当前环境不支持语音录制')
    return
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    noteMediaStream = stream
    const mimeType = pickVoiceMimeType()
    noteMediaRecorder = mimeType
      ? new MediaRecorder(stream, { mimeType })
      : new MediaRecorder(stream)
    noteVoiceChunks = []
    noteRecordStart = Date.now()
    isRecordingNote.value = true
    noteMediaRecorder.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) noteVoiceChunks.push(ev.data)
    }
    noteMediaRecorder.start(200)
    noteVoiceMaxTimer = window.setTimeout(() => {
      void finishNoteVoice(false)
    }, VOICE_MAX_SECONDS * 1000)
    message.info('正在录音，再次点击结束')
  } catch {
    resetNoteVoice()
    message.error('无法使用麦克风，请检查权限')
  }
}

function onMoreSelect(key: string) {
  if (key === 'image') imageInputRef.value?.click()
  else if (key === 'location') {
    showLocationPicker.value = true
  } else if (key === 'clear') {
    content.value = ''
    title.value = '无标题'
    pushHistorySnapshot()
    message.success('笔记已清空')
  }
}

function onLocationPicked(location: string) {
  showLocationPicker.value = false
  if (!location?.trim()) return
  insertBlock(`[位置: ${location.trim()}]`)
  message.success('位置已插入')
}

function onNoteSelect(key: string) {
  if (key === 'new') {
    noteStore.newNote()
    historyStack.value = ['']
    historyIndex.value = 0
  } else {
    const note = notes.value.find(n => n.id === key)
    if (note) {
      noteStore.openNote(note)
      historyStack.value = [content.value]
      historyIndex.value = 0
    }
  }
  showNoteList.value = false
}

async function deleteCurrentNote() {
  if (!currentNoteId.value) return
  try {
    await noteStore.deleteNote(currentNoteId.value)
    message.success('笔记已删除')
  } catch {
    message.error('删除失败')
  }
}

onMounted(async () => {
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  await noteStore.init()
  if (!currentNoteId.value) {
    noteStore.loadDraft()
  }
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
  void noteStore.save()
  if (saveTimer) clearTimeout(saveTimer)
  if (historyTimer) clearTimeout(historyTimer)
  if (isRecordingNote.value) {
    void finishNoteVoice(true)
  } else {
    resetNoteVoice()
  }
})
</script>

<template>
  <div class="note-editor standalone-window">
    <div v-if="showLocationPicker" class="location-overlay">
      <LocationPickerPage @select="onLocationPicked" @back="showLocationPicker = false" />
    </div>
    <header class="title-bar drag-area">
      <div class="bar-side bar-left no-drag">
        <button
          type="button"
          class="icon-btn pin"
          :class="{ active: isPinned }"
          title="置顶"
          @click="togglePin"
        >
          <PinIcon :size="14" />
        </button>
      </div>
      <div class="bar-center">
        {{ title || '无标题' }}
        <span v-if="saving" class="saving-indicator">保存中...</span>
      </div>
      <div class="bar-side bar-right no-drag">
        <!-- CRUD 操作按钮 -->
        <button type="button" class="icon-btn" title="新建笔记" @click="noteStore.newNote()">
          <n-icon :component="AddOutline" :size="16" />
        </button>
        <button type="button" class="icon-btn" title="保存笔记" @click="() => noteStore.save()">
          <n-icon :component="CloudUploadOutline" :size="16" />
        </button>
        <n-dropdown trigger="click" :options="noteListOptions" @select="onNoteSelect">
          <button type="button" class="icon-btn" title="打开笔记">
            <n-icon :component="DocumentTextOutline" :size="16" />
          </button>
        </n-dropdown>
        <n-dropdown trigger="click" :options="moreOptions" @select="onMoreSelect">
          <button type="button" class="icon-btn" title="更多操作">
            <n-icon :component="EllipsisHorizontalOutline" :size="16" />
          </button>
        </n-dropdown>
        <button
          v-if="currentNoteId"
          type="button"
          class="icon-btn delete-btn"
          title="删除笔记"
          @click="deleteCurrentNote"
        >
          <n-icon :component="TrashOutline" :size="16" />
        </button>
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

      <span class="v-sep" />

      <button
        type="button"
        class="icon-btn"
        :class="{ active: showPreview }"
        title="实时预览"
        @click="showPreview = !showPreview"
      >
        <n-icon :component="EyeOutline" :size="18" />
      </button>
    </div>

    <main class="editor-area">
      <textarea
        v-show="!showPreview || content"
        ref="contentRef"
        v-model="content"
        class="editor-input"
        :class="{ 'half-width': showPreview }"
        placeholder="按住 Ctrl + Win 可以使用语音输入文字，记录的文字、图片等内容将自动保存。"
      />
      <div v-if="showPreview" class="markdown-preview markdown-body" v-html="compiledMarkdown"></div>
    </main>
  </div>
</template>

<style scoped>
.note-editor {
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
  border-radius: var(--lx-radius);
  overflow: hidden;
}

.location-overlay {
  position: absolute;
  inset: 0;
  z-index: 50;
  background: var(--lx-bg-panel);
}

.title-bar {
  height: env(titlebar-area-height, 40px);
  width: env(titlebar-area-width, 100%);
  margin-left: env(titlebar-area-x, 0px);
  box-sizing: border-box;
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
  gap: 4px;
  -webkit-app-region: no-drag;
  position: relative;
  z-index: 10;
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
  display: flex;
  align-items: center;
  gap: 8px;
}

.saving-indicator {
  font-size: 11px;
  color: var(--lx-text-muted);
  font-weight: normal;
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
  -webkit-app-region: no-drag;
  pointer-events: auto;
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

.icon-btn.delete-btn:hover {
  color: var(--lx-danger);
  background: rgba(250, 81, 81, 0.1);
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
  -webkit-app-region: no-drag;
  position: relative;
  z-index: 10;
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

.editor-input.half-width {
  width: 50%;
  flex: 0 0 50%;
  border-right: 1px solid var(--lx-border-light);
}

.markdown-preview {
  flex: 1;
  width: 50%;
  padding: 14px 18px 20px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.65;
  color: var(--lx-text-body);
  background: var(--lx-bg-panel);
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
  color: var(--lx-text);
}

.markdown-preview :deep(p) {
  margin-bottom: 1em;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  padding-left: 2em;
  margin-bottom: 1em;
}

.markdown-preview :deep(img) {
  max-width: 100%;
  border-radius: var(--lx-radius);
}

.markdown-preview :deep(blockquote) {
  border-left: 4px solid var(--lx-accent);
  padding-left: 1em;
  color: var(--lx-text-secondary);
  margin-left: 0;
  background: var(--lx-bg-hover);
  padding: 8px 12px;
  border-radius: 0 var(--lx-radius) var(--lx-radius) 0;
}

.editor-input::placeholder {
  color: var(--lx-text-muted);
  font-size: 13px;
}
</style>
