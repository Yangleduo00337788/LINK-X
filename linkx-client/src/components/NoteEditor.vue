<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 笔记编辑器独立窗口
 */
import { ref, onMounted, onUnmounted, watch, computed, nextTick, type ComponentPublicInstance } from 'vue'
import { NIcon, NDropdown, useMessage } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import {
  FolderOpenOutline,
  ImageOutline,
  ListOutline,
  CheckboxOutline,
  TextOutline,
  ArrowUndoOutline,
  ArrowRedoOutline,
  EllipsisHorizontalOutline,
  ReorderTwoOutline,
  TrashOutline,
  AddOutline,
  DocumentTextOutline,
  CloudUploadOutline
} from '@vicons/ionicons5'
import NoteTextBlockEditor from './NoteTextBlockEditor.vue'
import WindowCaptionButtons from './WindowCaptionButtons.vue'
import { storeToRefs } from 'pinia'
import { useNoteStore } from '../stores/note'
import { useAppStore } from '../stores/app'
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
import * as noteApi from '../api/note'
import LocationPickerPage from './LocationPickerPage.vue'
import { useI18n } from '../i18n'
import { deriveNoteTitle } from '../utils/noteTitle'
import {
  fileKindFromName,
  fileKindLabel,
  mediaKeyFromRef,
  parseAttachmentMeta,
  parseNoteBlocks,
  serializeNoteBlocks
} from '../utils/noteBlocks'
import { formatFileSize } from '../utils/file'
import { emptyFormatState, type NoteFormatAction, type NoteFormatState } from '../utils/noteEditorFormat'
import { LxIconButton } from './ui'
import { registerAgentNoteEditorBridge } from '../linkmateAgent/uiBridge'

const props = withDefaults(
  defineProps<{
    embedded?: boolean
    standalone?: boolean
    dockEmbed?: boolean
  }>(),
  {
    embedded: false,
    standalone: false,
    dockEmbed: false
  }
)

const isEmbedded = computed(() => props.embedded)
const isDockEmbed = computed(() => props.dockEmbed)
const showTitleBar = computed(() => !props.dockEmbed && !(props.embedded && props.standalone))
const showActionBar = computed(() => !showTitleBar.value)

type NoteTextBlockEditorInstance = ComponentPublicInstance & {
  focus: () => void
  focusEnd: () => void
  forceSyncFromModel: () => void
  prepareToolbarAction: () => void
  runFormat: (action: NoteFormatAction) => void
  insertText: (text: string) => void
}

function syncAllTextBlocks() {
  for (const editor of Object.values(textBlockRefs.value)) {
    editor?.forceSyncFromModel?.()
  }
}

const message = useMessage()
const { t } = useI18n()
const noteStore = useNoteStore()
const appStore = useAppStore()
const { title, content, notes, currentNoteId, saving, activeTabId } = storeToRefs(noteStore)
const { theme } = storeToRefs(appStore)

const documentEl = ref<HTMLElement | null>(null)
const showNoteList = ref(false)
const showLocationPicker = ref(false)
const mediaUrlCache = ref<Record<string, string>>({})
const uploadingMedia = ref(false)
const activeTextBlockIndex = ref(0)
const textBlockRefs = ref<Record<number, NoteTextBlockEditorInstance | null>>({})
const formatActive = ref<NoteFormatState>(emptyFormatState())

const noteBlocks = computed(() => {
  const blocks = parseNoteBlocks(content.value)
  const last = blocks[blocks.length - 1]
  if (!last || last.type !== 'text') {
    return [...blocks, { type: 'text' as const, value: '' }]
  }
  return blocks
})

const parsedBlockCount = computed(() => parseNoteBlocks(content.value).length)

function isVirtualTextBlock(index: number): boolean {
  return index >= parsedBlockCount.value
}

function setTextBlockRef(index: number, el: Element | ComponentPublicInstance | null) {
  if (el && typeof el === 'object' && 'runFormat' in el) {
    textBlockRefs.value[index] = el as NoteTextBlockEditorInstance
  } else {
    delete textBlockRefs.value[index]
  }
}

function ensureActiveEditor(): NoteTextBlockEditorInstance | null {
  let editor = getActiveTextBlockEditor()
  if (editor) return editor
  const firstIndex = noteBlocks.value.findIndex(block => block.type === 'text')
  if (firstIndex < 0) return null
  activeTextBlockIndex.value = firstIndex
  editor = textBlockRefs.value[firstIndex] ?? null
  editor?.focusEnd()
  return editor
}

function runFormat(action: NoteFormatAction) {
  const editor = ensureActiveEditor()
  if (!editor) return
  editor.prepareToolbarAction()
  editor.runFormat(action)
}

function getActiveTextBlockEditor(): NoteTextBlockEditorInstance | null {
  return textBlockRefs.value[activeTextBlockIndex.value] ?? Object.values(textBlockRefs.value)[0] ?? null
}

function onToolbarPointerDown() {
  ensureActiveEditor()?.prepareToolbarAction()
}

function onFormatState(index: number, state: NoteFormatState) {
  if (index === activeTextBlockIndex.value) {
    formatActive.value = state
  }
}

function resolveMediaSrc(ref: string): string {
  if (/^https?:\/\//i.test(ref)) return ref
  const key = mediaKeyFromRef(ref)
  return mediaUrlCache.value[key] || ''
}

function attachmentHref(ref: string): string {
  const url = resolveMediaSrc(ref)
  return url || '#'
}

function scrollDocumentToEnd() {
  void nextTick(() => {
    documentEl.value?.scrollTo({ top: documentEl.value.scrollHeight })
    getActiveTextBlockEditor()?.focusEnd()
  })
}

function onTextBlockValueChange(index: number, value: string) {
  if (isVirtualTextBlock(index)) {
    const prefix = content.value && !content.value.endsWith('\n') ? '\n' : ''
    content.value = content.value + prefix + value
    return
  }
  const blocks = parseNoteBlocks(content.value)
  const block = blocks[index]
  if (block?.type !== 'text') return
  blocks[index] = { type: 'text', value }
  content.value = serializeNoteBlocks(blocks)
}

function removeBlockAt(index: number) {
  if (isVirtualTextBlock(index)) return
  const blocks = parseNoteBlocks(content.value)
  const block = blocks[index]
  if (!block || block.type === 'text') return
  blocks.splice(index, 1)
  content.value = serializeNoteBlocks(blocks)
  pushHistorySnapshot()
  message.success(t('noteEditor.blockRemoved'))
}

function focusLastTextBlock() {
  const blocks = noteBlocks.value
  for (let i = blocks.length - 1; i >= 0; i--) {
    if (blocks[i]?.type === 'text') {
      activeTextBlockIndex.value = i
      void nextTick(() => {
        textBlockRefs.value[i]?.focusEnd()
      })
      return
    }
  }
}

function insertBlock(text: string) {
  const prefix = content.value && !content.value.endsWith('\n') ? '\n' : ''
  const editor = ensureActiveEditor()
  if (editor) {
    editor.insertText(prefix + text + '\n')
    return
  }
  const index = activeTextBlockIndex.value
  const block = noteBlocks.value[index]
  const current = block?.type === 'text' ? block.value : ''
  onTextBlockValueChange(index, current + prefix + text + '\n')
}

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
let restoringHistory = false

const historyStack = ref<string[]>([''])
const historyIndex = ref(0)

const moreOptions = computed<DropdownOption[]>(() => [
  { label: t('noteEditor.insertImage'), key: 'image' },
  { label: t('noteEditor.insertLocation'), key: 'location' },
  { type: 'divider', key: 'd1' },
  { label: t('noteEditor.clearNote'), key: 'clear' }
])

const noteListOptions = computed<DropdownOption[]>(() => [
  { label: t('noteEditor.newNote'), key: 'new' },
  ...notes.value.slice(0, 10).map(n => ({
    label: n.title || t('noteEditor.untitled'),
    key: n.id
  }))
])

const displayTitle = computed(
  () => deriveNoteTitle(content.value) || t('noteEditor.untitled')
)

function syncTitleFromContent() {
  title.value = deriveNoteTitle(content.value) || t('defaults.untitled')
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
  void nextTick(syncAllTextBlocks)
  void noteStore.save()
}

function redo() {
  if (historyIndex.value >= historyStack.value.length - 1) return
  restoringHistory = true
  historyIndex.value += 1
  content.value = historyStack.value[historyIndex.value] ?? ''
  restoringHistory = false
  void nextTick(syncAllTextBlocks)
  void noteStore.save()
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
      throw new Error(res.message || t('noteEditor.uploadFail'))
    }
    if (res.data.url) {
      mediaUrlCache.value = { ...mediaUrlCache.value, [res.data.fileKey]: res.data.url }
    }
    insertBlock(`![${file.name}](lx-media:${res.data.fileKey})`)
    scrollDocumentToEnd()
    message.success(t('noteEditor.imageInserted'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('noteEditor.imageUploadFail'))
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
      throw new Error(res.message || t('noteEditor.uploadFail'))
    }
    if (res.data.url) {
      mediaUrlCache.value = { ...mediaUrlCache.value, [res.data.fileKey]: res.data.url }
    }
    insertBlock(`[${t('noteEditor.attachmentMd', { name: file.name })} · ${formatFileSize(file.size)}](lx-media:${res.data.fileKey})`)
    scrollDocumentToEnd()
    message.success(t('noteEditor.attachmentInserted'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('noteEditor.attachmentUploadFail'))
  } finally {
    uploadingMedia.value = false
  }
}

async function resetToBlankEditor() {
  await flushPendingSave()
  noteStore.prepareBlankEditor()
  historyStack.value = ['']
  historyIndex.value = 0
  mediaUrlCache.value = {}
}

function onMoreSelect(key: string) {
  if (key === 'image') imageInputRef.value?.click()
  else if (key === 'location') {
    showLocationPicker.value = true
  } else if (key === 'clear') {
    content.value = ''
    title.value = t('noteEditor.untitled')
    pushHistorySnapshot()
    message.success(t('noteEditor.noteCleared'))
  }
}

function onLocationPicked(location: string) {
  showLocationPicker.value = false
  if (!location?.trim()) return
  insertBlock(`[${t('noteEditor.locationMd', { location: location.trim() })}]`)
  message.success(t('noteEditor.locationInserted'))
}

function onNoteSelect(key: string) {
  if (key === 'new') {
    void handleNewNote()
  } else if (isEmbedded.value) {
    noteStore.registerOpenTab(key)
    void noteStore.selectTab(key)
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
    message.success(t('noteEditor.noteDeleted'))
  } catch {
    message.error(t('noteEditor.deleteFail'))
  }
}

async function flushPendingSave() {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  syncAllTextBlocks()
  if (!content.value.trim() && !currentNoteId.value) return
  syncTitleFromContent()
  await noteStore.save()
}

async function handleNewNote() {
  if (isEmbedded.value) {
    await flushPendingSave()
    noteStore.openNewTab()
    return
  }
  void resetToBlankEditor()
}

async function handleSaveNote() {
  await flushPendingSave()
}

async function loadActiveTabContent() {
  const tabId = activeTabId.value
  if (!tabId || tabId === 'new') {
    if (currentNoteId.value !== null || content.value.trim()) {
      noteStore.newNote()
    }
    historyStack.value = [content.value || '']
    historyIndex.value = 0
    mediaUrlCache.value = {}
    return
  }
  if (currentNoteId.value !== tabId) {
    await noteStore.openNoteById(tabId)
    historyStack.value = [content.value]
    historyIndex.value = 0
    mediaUrlCache.value = {}
  }
}


onMounted(async () => {
  if (!isEmbedded.value) {
    applyDocumentTheme(appStore.theme)
    notifyElectronTheme(appStore.theme)
  }
  await noteStore.init()
  if (isEmbedded.value) {
    await loadActiveTabContent()
  } else {
    await resetToBlankEditor()
  }
  requestAnimationFrame(() => {
    getActiveTextBlockEditor()?.focus()
  })

  registerAgentNoteEditorBridge({
    isOpen: () => noteStore.openTabIds.length > 0,
    focusContent: () => {
      getActiveTextBlockEditor()?.focus()
    },
    setContent: (text: string) => {
      content.value = serializeNoteBlocks([{ type: 'text', value: text }])
    },
    save: async () => {
      await handleSaveNote()
      return true
    }
  })
})

watch(activeTabId, async (tabId, prev) => {
  if (!isEmbedded.value || !prev || tabId === prev) return
  await flushPendingSave()
  await loadActiveTabContent()
  requestAnimationFrame(() => {
    getActiveTextBlockEditor()?.focus()
  })
})

watch(theme, t => {
  if (!isEmbedded.value) {
    applyDocumentTheme(t)
    notifyElectronTheme(t)
  }
})

onUnmounted(() => {
  registerAgentNoteEditorBridge(null)
  void flushPendingSave()
  if (historyTimer) clearTimeout(historyTimer)
})
</script>

<template>
  <div
    class="note-editor"
    :class="{
      'standalone-window': !isEmbedded,
      'note-editor--embedded': isEmbedded,
      'note-editor--dock': isDockEmbed
    }"
  >
    <div v-if="showLocationPicker" class="location-overlay">
      <LocationPickerPage @select="onLocationPicked" @back="showLocationPicker = false" />
    </div>
    <header v-if="showTitleBar" class="title-bar drag-area">
      <div class="bar-side bar-left no-drag" />
      <div class="bar-center">
        {{ displayTitle }}
        <span v-if="saving" class="saving-indicator">{{ t('noteEditor.saving') }}</span>
      </div>
      <div class="bar-side bar-right no-drag">
        <!-- CRUD 操作按钮 -->
        <LxIconButton variant="editor" :title="t('noteEditor.newNote')" @click="handleNewNote">
          <n-icon :component="AddOutline" :size="16" />
        </LxIconButton>
        <LxIconButton variant="editor" data-lm-note-save :title="t('noteEditor.saveNote')" @click="handleSaveNote">
          <n-icon :component="CloudUploadOutline" :size="16" />
        </LxIconButton>
        <n-dropdown trigger="click" :options="noteListOptions" @select="onNoteSelect">
          <LxIconButton variant="editor" :title="t('noteEditor.openNote')">
            <n-icon :component="DocumentTextOutline" :size="16" />
          </LxIconButton>
        </n-dropdown>
        <n-dropdown trigger="click" :options="moreOptions" @select="onMoreSelect">
          <LxIconButton variant="editor" :title="t('noteEditor.moreActions')">
            <n-icon :component="EllipsisHorizontalOutline" :size="16" />
          </LxIconButton>
        </n-dropdown>
        <LxIconButton
          v-if="currentNoteId"
          variant="editor"
          class="delete-btn"
          :title="t('noteEditor.deleteNote')"
          @click="deleteCurrentNote"
        >
          <n-icon :component="TrashOutline" :size="16" />
        </LxIconButton>
        <WindowCaptionButtons v-if="!isEmbedded" show-pin :before-close="flushPendingSave" />
      </div>
    </header>

    <div v-if="showActionBar" class="note-action-bar no-drag">
      <button type="button" class="note-action-btn" :title="t('noteEditor.newNote')" @click="handleNewNote">
        <n-icon :component="AddOutline" :size="15" />
        <span>{{ t('noteEditor.newNote') }}</span>
      </button>
      <button type="button" class="note-action-btn" data-lm-note-save :title="t('noteEditor.saveNote')" @click="handleSaveNote">
        <n-icon :component="CloudUploadOutline" :size="15" />
        <span>{{ t('noteEditor.saveNote') }}</span>
      </button>
      <n-dropdown trigger="click" :options="noteListOptions" @select="onNoteSelect">
        <button type="button" class="note-action-btn" :title="t('noteEditor.openNote')">
          <n-icon :component="DocumentTextOutline" :size="15" />
          <span>{{ t('noteEditor.openNote') }}</span>
        </button>
      </n-dropdown>
      <n-dropdown trigger="click" :options="moreOptions" @select="onMoreSelect">
        <button type="button" class="note-action-btn note-action-btn--icon" :title="t('noteEditor.moreActions')">
          <n-icon :component="EllipsisHorizontalOutline" :size="16" />
        </button>
      </n-dropdown>
      <button
        v-if="currentNoteId"
        type="button"
        class="note-action-btn note-action-btn--icon note-action-btn--danger"
        :title="t('noteEditor.deleteNote')"
        @click="deleteCurrentNote"
      >
        <n-icon :component="TrashOutline" :size="15" />
      </button>
      <span class="note-action-spacer" />
      <span class="note-action-title">{{ displayTitle }}</span>
      <span v-if="saving" class="saving-indicator">{{ t('noteEditor.saving') }}</span>
    </div>

    <div class="format-bar no-drag" @mousedown="onToolbarPointerDown">
      <input ref="imageInputRef" type="file" accept="image/*" hidden @change="insertImage" />
      <input ref="fileInputRef" type="file" hidden @change="insertFile" />

      <LxIconButton variant="editor" :title="t('noteEditor.insertImage')" @mousedown.prevent @click="imageInputRef?.click()">
        <n-icon :component="ImageOutline" :size="18" />
      </LxIconButton>
      <LxIconButton variant="editor" :title="t('noteEditor.attachment')" @mousedown.prevent @click="fileInputRef?.click()">
        <n-icon :component="FolderOpenOutline" :size="18" />
      </LxIconButton>

      <span class="v-sep" />

      <button type="button" class="lx-btn--format-text" :class="{ 'is-active': formatActive.bold }" :title="t('noteEditor.bold')" @mousedown.prevent @click="runFormat('bold')">B</button>
      <LxIconButton variant="editor" :active="formatActive.heading" :title="t('noteEditor.heading')" @mousedown.prevent @click="runFormat('heading')">
        <n-icon :component="TextOutline" :size="17" />
      </LxIconButton>
      <button type="button" class="lx-btn--format-text is-underline" :class="{ 'is-active': formatActive.underline }" :title="t('noteEditor.underline')" @mousedown.prevent @click="runFormat('underline')">U</button>
      <button type="button" class="lx-btn--format-text is-italic" :class="{ 'is-active': formatActive.italic }" :title="t('noteEditor.italic')" @mousedown.prevent @click="runFormat('italic')">I</button>

      <span class="v-sep" />

      <LxIconButton variant="editor" :title="t('noteEditor.divider')" @mousedown.prevent @click="runFormat('divider')">
        <n-icon :component="ReorderTwoOutline" :size="18" />
      </LxIconButton>
      <LxIconButton
        variant="editor"
        :active="formatActive.unordered"
        :title="t('noteEditor.unorderedList')"
        @mousedown.prevent
        @click="runFormat('unordered')"
      >
        <n-icon :component="ListOutline" :size="18" />
      </LxIconButton>
      <LxIconButton
        variant="editor"
        :active="formatActive.ordered"
        :title="t('noteEditor.orderedList')"
        @mousedown.prevent
        @click="runFormat('ordered')"
      >
        <span class="num-list">1.</span>
      </LxIconButton>
      <LxIconButton variant="editor" :title="t('noteEditor.todoList')" @mousedown.prevent @click="runFormat('todo')">
        <n-icon :component="CheckboxOutline" :size="18" />
      </LxIconButton>

      <span class="v-sep" />

      <LxIconButton variant="editor" :title="t('noteEditor.undo')" @mousedown.prevent @click="undo">
        <n-icon :component="ArrowUndoOutline" :size="18" />
      </LxIconButton>
      <LxIconButton variant="editor" :title="t('noteEditor.redo')" @mousedown.prevent @click="redo">
        <n-icon :component="ArrowRedoOutline" :size="18" />
      </LxIconButton>
    </div>

    <main ref="documentEl" class="editor-area" data-lm-note-content @click="focusLastTextBlock">
      <div class="note-document">
        <template v-for="(block, index) in noteBlocks" :key="index">
          <NoteTextBlockEditor
            v-if="block.type === 'text'"
            :ref="el => setTextBlockRef(index, el)"
            :model-value="block.value"
            :media-cache="mediaUrlCache"
            :placeholder="index === 0 && !content.trim() ? t('noteEditor.placeholder') : undefined"
            @update:model-value="onTextBlockValueChange(index, $event)"
            @focus="activeTextBlockIndex = index"
            @format-state="onFormatState(index, $event)"
          />
          <figure v-else-if="block.type === 'image'" class="note-image-block" @click.stop>
            <img :src="resolveMediaSrc(block.ref)" :alt="block.alt" loading="lazy" />
            <button
              type="button"
              class="note-block-remove"
              :title="t('noteEditor.removeImage')"
              @click="removeBlockAt(index)"
            >
              <n-icon :component="TrashOutline" :size="14" />
            </button>
          </figure>
          <a
            v-else-if="block.type === 'attachment'"
            class="note-attach-card"
            :href="attachmentHref(block.ref)"
            target="_blank"
            rel="noopener noreferrer"
            @click.stop
          >
            <span
              class="note-attach-icon"
              :class="`note-attach-icon--${fileKindFromName(parseAttachmentMeta(block.label).fileName)}`"
            >
              {{ fileKindLabel(fileKindFromName(parseAttachmentMeta(block.label).fileName)) }}
            </span>
            <span class="note-attach-meta">
              <span class="note-attach-name">{{ parseAttachmentMeta(block.label).fileName }}</span>
              <span v-if="parseAttachmentMeta(block.label).size" class="note-attach-size">
                {{ parseAttachmentMeta(block.label).size }}
              </span>
            </span>
            <button
              type="button"
              class="note-block-remove"
              :title="t('noteEditor.removeAttachment')"
              @click.prevent.stop="removeBlockAt(index)"
            >
              <n-icon :component="TrashOutline" :size="14" />
            </button>
          </a>
          <span v-else-if="block.type === 'location'" class="note-location-wrap" @click.stop>
            <span class="note-location-chip">📍 {{ block.place }}</span>
            <button
              type="button"
              class="note-block-remove note-block-remove--inline"
              :title="t('noteEditor.removeLocation')"
              @click="removeBlockAt(index)"
            >
              <n-icon :component="TrashOutline" :size="12" />
            </button>
          </span>
        </template>
      </div>
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

.note-editor--embedded {
  width: 100%;
  height: 100%;
  border-radius: 0;
}

.location-overlay {
  position: absolute;
  inset: 0;
  z-index: var(--lx-z-fab);
  background: var(--lx-bg-panel);
}

.title-bar {
  height: 40px;
  width: 100%;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: stretch;
  padding: 0 0 0 var(--lx-space);
  flex-shrink: 0;
  -webkit-app-region: drag;
  user-select: none;
}

.bar-side {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: var(--lx-space-xs);
  -webkit-app-region: no-drag;
  position: relative;
  z-index: var(--lx-z-dropdown);
}

.bar-left {
  justify-content: flex-start;
}

.bar-right {
  justify-content: flex-end;
  align-items: stretch;
  gap: 0;
}

.bar-center {
  font-size: var(--lx-font-md);
  font-weight: 500;
  color: var(--lx-text-body);
  text-align: center;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.note-action-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 40px;
  padding: 0 10px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

.note-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border: none;
  border-radius: var(--lx-radius-sm, 6px);
  background: transparent;
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-sm);
  cursor: pointer;
  white-space: nowrap;
}

.note-action-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.note-action-btn--icon {
  width: 28px;
  padding: 0;
  justify-content: center;
}

.note-action-btn--danger:hover {
  color: var(--lx-danger);
  background: var(--lx-danger-bg);
}

.note-action-spacer {
  flex: 1;
  min-width: 8px;
}

.note-action-title {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.saving-indicator {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  font-weight: normal;
}

.lx-action-btn--editor.is-active,
.lx-action-btn--editor.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}

.format-bar {
  display: flex;
  align-items: center;
  gap: var(--lx-space-2xs);
  padding: var(--lx-space-xs) var(--lx-space-md);
  flex-shrink: 0;
  overflow-x: auto;
  border-bottom: 1px solid var(--lx-border-light);
  -webkit-app-region: no-drag;
  position: relative;
  z-index: var(--lx-z-dropdown);
}

.v-sep {
  width: 1px;
  height: 18px;
  background: var(--lx-border-light);
  margin: 0 var(--lx-space-xs);
  flex-shrink: 0;
}

.num-list {
  font-size: var(--lx-font-md);
  font-weight: 600;
  line-height: var(--lx-leading-none);
  font-family: inherit;
}

.editor-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: var(--lx-bg-card);
}

.note-document {
  position: relative;
  padding: var(--lx-space-xl) var(--lx-space-2xl) var(--lx-space-4xl);
  min-height: 100%;
  box-sizing: border-box;
}

.editor-placeholder {
  margin: 0 0 var(--lx-space);
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-text-muted);
  pointer-events: none;
}

.note-image-block {
  position: relative;
  margin: 0 0 var(--lx-space-lg);
}

.note-image-block img {
  display: block;
  width: 100%;
  max-width: 100%;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-panel);
}

.note-block-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--lx-radius-xs);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.62);
  color: var(--lx-text-on-accent);
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--lx-duration), background var(--lx-duration);
  z-index: var(--lx-z-raised-2);
}

.note-image-block:hover .note-block-remove,
.note-attach-card:hover .note-block-remove {
  opacity: 1;
}

.note-block-remove:hover {
  background: rgba(220, 38, 38, 0.92);
}

.note-block-remove--inline {
  position: static;
  opacity: 1;
  width: 22px;
  height: 22px;
  background: transparent;
  color: var(--lx-text-muted);
}

.note-block-remove--inline:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-danger);
}

.note-attach-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-lg) var(--lx-space-xl);
  margin: 0 0 var(--lx-space-md);
  border-radius: var(--lx-radius-xl);
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
  text-decoration: none;
  color: inherit;
  max-width: 360px;
}

.note-attach-card:hover {
  border-color: var(--lx-accent);
  background: var(--lx-bg-hover);
}

.note-attach-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--lx-radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lx-font-xs);
  font-weight: 700;
  flex-shrink: 0;
  color: var(--lx-text-on-accent);
}

.note-attach-icon--pdf {
  background: var(--lx-danger);
}

.note-attach-icon--word {
  background: var(--lx-note-blue);
}

.note-attach-icon--ppt {
  background: var(--lx-note-orange);
}

.note-attach-icon--zip {
  background: var(--lx-note-amber);
}

.note-attach-icon--audio {
  background: var(--lx-brand-purple);
}

.note-attach-icon--text,
.note-attach-icon--file {
  background: var(--lx-slate);
}

.note-attach-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
}

.note-attach-name {
  font-size: var(--lx-font-md);
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-attach-size {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.note-location-wrap {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-2xs);
  margin: 0 0 var(--lx-space-md);
}

.note-location-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  padding: var(--lx-space-xs) var(--lx-space-md);
  margin: 0;
  border-radius: var(--lx-radius-pill);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-size: var(--lx-font-md);
}
</style>
