<script setup lang="ts">
// Vue 响应式 API、生命周期、侦听器与计算属性
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
// Naive UI 图标、下拉菜单与消息提示
import { NIcon, NDropdown, useMessage } from 'naive-ui'
// Naive UI 下拉选项类型
import type { DropdownOption } from 'naive-ui'
// Ionicons5 编辑器工具栏图标
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
  EyeOutline
} from '@vicons/ionicons5'
// 置顶图标组件
import PinIcon from './icons/PinIcon.vue'
// 窗口控制按钮组件
import WindowControls from './WindowControls.vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 笔记 Store
import { useNoteStore } from '../stores/note'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 主题同步工具
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
// Markdown 解析库
import { marked } from 'marked'
// HTML 消毒库（防 XSS）
import DOMPurify from 'dompurify'

// 消息提示实例
const message = useMessage()
// 笔记 Store 实例
const noteStore = useNoteStore()
// 应用 Store 实例
const appStore = useAppStore()
// 笔记标题与正文
const { title, content } = storeToRefs(noteStore)
// 当前主题
const { theme } = storeToRefs(appStore)

// 正文 textarea 元素引用
const contentRef = ref<HTMLTextAreaElement | null>(null)
// 窗口是否置顶
const isPinned = ref(false)
// 是否正在录音
const isRecordingNote = ref(false)
// 是否显示 Markdown 预览
const showPreview = ref(false)

// 将 Markdown 正文编译为消毒后的 HTML
const compiledMarkdown = computed(() => {
  const rawHtml = marked(content.value) as string
  return DOMPurify.sanitize(rawHtml)
})

// 隐藏的图片上传 input 引用
const imageInputRef = ref<HTMLInputElement | null>(null)
// 隐藏的文件上传 input 引用
const fileInputRef = ref<HTMLInputElement | null>(null)

// 自动保存防抖定时器
let saveTimer: ReturnType<typeof setTimeout> | null = null
// 历史记录防抖定时器
let historyTimer: ReturnType<typeof setTimeout> | null = null
// 录音开始时间戳
let noteRecordStart = 0
// 是否正在执行撤销/重做（避免重复入栈）
let restoringHistory = false

// 编辑历史栈
const historyStack = ref<string[]>([''])
// 当前历史栈索引
const historyIndex = ref(0)

// 「更多」下拉菜单选项
const moreOptions: DropdownOption[] = [
  { label: '插入图片', key: 'image' },
  { label: '插入位置', key: 'location' },
  { label: '清空笔记', key: 'clear' }
]

// 从正文首行同步标题
function syncTitleFromContent() {
  const first = content.value.split('\n').find(line => line.trim())?.trim() ?? ''
  title.value = first.slice(0, 80) || '笔记'
}

// 防抖自动保存
function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    syncTitleFromContent()
    noteStore.save()
  }, 400)
}

// 将当前内容快照推入历史栈
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

// 防抖记录历史
function scheduleHistory() {
  if (historyTimer) clearTimeout(historyTimer)
  historyTimer = setTimeout(pushHistorySnapshot, 600)
}

// 正文变化时触发自动保存与历史记录
watch(content, () => {
  scheduleSave()
  scheduleHistory()
})

// 撤销上一步编辑
function undo() {
  if (historyIndex.value <= 0) return
  restoringHistory = true
  historyIndex.value -= 1
  content.value = historyStack.value[historyIndex.value] ?? ''
  restoringHistory = false
  noteStore.save()
}

// 重做下一步编辑
function redo() {
  if (historyIndex.value >= historyStack.value.length - 1) return
  restoringHistory = true
  historyIndex.value += 1
  content.value = historyStack.value[historyIndex.value] ?? ''
  restoringHistory = false
  noteStore.save()
}

// 用前缀/后缀包裹选中文本（Markdown 格式）
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

// 在光标处插入文本
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

// 插入块级文本（自动补换行）
function insertBlock(text: string) {
  insertAtCursor((content.value && !content.value.endsWith('\n') ? '\n' : '') + text + '\n')
}

// 切换窗口置顶状态
async function togglePin() {
  if (!window.electronAPI?.togglePin) return
  isPinned.value = await window.electronAPI.togglePin()
}

// 插入图片占位标记
function insertImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  insertBlock(`[图片: ${file.name}]`)
  message.success('图片已插入')
}

// 插入附件占位标记
function insertFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  insertBlock(`[附件: ${file.name}]`)
  message.success('附件已插入')
}

// 切换语音备忘录音（无麦克风时插入占位文本）
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

// 处理「更多」菜单选项
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

// 挂载：加载笔记、同步主题、读取置顶状态
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

// 主题变化时重新同步
watch(theme, t => {
  applyDocumentTheme(t)
  notifyElectronTheme(t)
})

// 卸载：保存笔记并清理定时器
onUnmounted(() => {
  syncTitleFromContent()
  noteStore.save()
  if (saveTimer) clearTimeout(saveTimer)
  if (historyTimer) clearTimeout(historyTimer)
})
</script>

<template>
  <!-- 笔记编辑器独立窗口 -->
  <div class="note-editor standalone-window">
    <!-- 标题栏：置顶、标题、窗口控制 -->
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

    <!-- 格式化工具栏 -->
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

    <!-- 编辑区：textarea 与 Markdown 预览 -->
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
