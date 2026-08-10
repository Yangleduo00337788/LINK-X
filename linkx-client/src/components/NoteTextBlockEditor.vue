<!-- 作者：yangleduo -->
<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { htmlToNoteMarkdown } from '../utils/noteHtmlToMarkdown'
import { renderNoteTextBlockHtml } from '../utils/noteTextPreview'
import { getCaretTextOffset, setCaretTextOffset } from '../utils/noteEditorCaret'
import {
  emptyFormatState,
  type NoteFormatAction,
  type NoteFormatState
} from '../utils/noteEditorFormat'

const props = defineProps<{
  modelValue: string
  placeholder?: string
  mediaCache: Record<string, string>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  focus: []
  'format-state': [state: NoteFormatState]
}>()

const editorEl = ref<HTMLDivElement | null>(null)
const isFocused = ref(false)
const isComposing = ref(false)
const formatState = ref<NoteFormatState>(emptyFormatState())
let syncing = false
let rerenderRaf = 0
let savedRange: Range | null = null

function renderHtml(markdown: string): string {
  return renderNoteTextBlockHtml(markdown, props.mediaCache)
}

function applyHtml(html: string, caret?: number) {
  if (!editorEl.value) return
  syncing = true
  editorEl.value.innerHTML = html
  if (caret !== undefined) {
    setCaretTextOffset(editorEl.value, caret)
  }
  syncing = false
}

function syncFromModel(force = false) {
  if (!editorEl.value || (isFocused.value && !force)) return
  applyHtml(renderHtml(props.modelValue))
}

function saveSelection() {
  const root = editorEl.value
  const sel = window.getSelection()
  if (!root || !sel || sel.rangeCount === 0) return
  const range = sel.getRangeAt(0)
  if (root.contains(range.startContainer) && root.contains(range.endContainer)) {
    savedRange = range.cloneRange()
  }
}

function restoreSelection() {
  if (!savedRange) return
  const sel = window.getSelection()
  sel?.removeAllRanges()
  sel?.addRange(savedRange)
}

function selectionInsideEditor(): boolean {
  const root = editorEl.value
  const sel = window.getSelection()
  if (!root || !sel || sel.rangeCount === 0) return false
  return root.contains(sel.anchorNode)
}

function refreshFormatState() {
  if (!editorEl.value || !selectionInsideEditor()) {
    formatState.value = emptyFormatState()
    emit('format-state', formatState.value)
    return
  }
  try {
    formatState.value = {
      bold: document.queryCommandState('bold'),
      italic: document.queryCommandState('italic'),
      underline: document.queryCommandState('underline'),
      heading: document.queryCommandValue('formatBlock').toLowerCase() === 'h2',
      unordered: document.queryCommandState('insertUnorderedList'),
      ordered: document.queryCommandState('insertOrderedList'),
      todo: false
    }
  } catch {
    formatState.value = emptyFormatState()
  }
  emit('format-state', formatState.value)
}

function rerenderFromEditor() {
  if (!editorEl.value || syncing || isComposing.value) return

  const caret = getCaretTextOffset(editorEl.value)
  const md = htmlToNoteMarkdown(editorEl.value.innerHTML)
  const html = renderHtml(md)

  if (editorEl.value.innerHTML !== html) {
    applyHtml(html, caret)
  }

  if (md !== props.modelValue) {
    emit('update:modelValue', md)
  }
  refreshFormatState()
}

function scheduleRerender() {
  cancelAnimationFrame(rerenderRaf)
  rerenderRaf = requestAnimationFrame(() => rerenderFromEditor())
}

function onInput() {
  scheduleRerender()
}

function onCompositionEnd() {
  isComposing.value = false
  scheduleRerender()
}

function onEditorClick(e: MouseEvent) {
  const target = e.target
  if (target instanceof HTMLInputElement && target.type === 'checkbox' && target.classList.contains('note-task-checkbox')) {
    e.preventDefault()
    target.checked = !target.checked
    scheduleRerender()
    return
  }
  saveSelection()
  refreshFormatState()
}

function onFocus() {
  isFocused.value = true
  emit('focus')
  refreshFormatState()
}

function onBlur() {
  isFocused.value = false
  rerenderFromEditor()
  void nextTick(() => syncFromModel(true))
}

function onKeyUp() {
  saveSelection()
  refreshFormatState()
}

function onMouseUp() {
  saveSelection()
  refreshFormatState()
}

function onKeyDown(e: KeyboardEvent) {
  if (!(e.ctrlKey || e.metaKey) || e.altKey) return
  const key = e.key.toLowerCase()
  if (key === 'b') {
    e.preventDefault()
    runFormat('bold')
  } else if (key === 'i') {
    e.preventDefault()
    runFormat('italic')
  } else if (key === 'u') {
    e.preventDefault()
    runFormat('underline')
  }
}

function runFormat(action: NoteFormatAction) {
  const root = editorEl.value
  if (!root) return

  root.focus()
  if (savedRange) {
    restoreSelection()
  } else {
    setCaretTextOffset(root, Number.MAX_SAFE_INTEGER)
  }

  switch (action) {
    case 'bold':
      document.execCommand('bold')
      break
    case 'italic':
      document.execCommand('italic')
      break
    case 'underline':
      document.execCommand('underline')
      break
    case 'heading':
      document.execCommand('formatBlock', false, 'h2')
      break
    case 'unordered':
      document.execCommand('insertUnorderedList')
      break
    case 'ordered':
      document.execCommand('insertOrderedList')
      break
    case 'todo':
      document.execCommand('insertText', false, '- [ ] ')
      break
    case 'divider':
      document.execCommand('insertHorizontalRule')
      break
  }

  saveSelection()
  scheduleRerender()
}

watch(
  () => props.modelValue,
  val => {
    if (!editorEl.value || isFocused.value) return
    const html = renderHtml(val)
    if (editorEl.value.innerHTML !== html) {
      applyHtml(html)
    }
  }
)

watch(
  () => props.mediaCache,
  () => {
    if (!isFocused.value) syncFromModel()
  },
  { deep: true }
)

onMounted(() => syncFromModel())
onUnmounted(() => cancelAnimationFrame(rerenderRaf))

function focusEditor() {
  editorEl.value?.focus()
}

function focusEnd() {
  focusEditor()
  if (editorEl.value) {
    setCaretTextOffset(editorEl.value, Number.MAX_SAFE_INTEGER)
  }
}

function insertText(text: string) {
  const root = editorEl.value
  if (!root) return
  root.focus()
  restoreSelection()
  document.execCommand('insertText', false, text)
  saveSelection()
  scheduleRerender()
}

defineExpose({
  focus: focusEditor,
  focusEnd,
  forceSyncFromModel: () => syncFromModel(true),
  prepareToolbarAction: saveSelection,
  runFormat,
  insertText
})
</script>

<template>
  <div
    ref="editorEl"
    class="note-text-editor"
    contenteditable="true"
    spellcheck="false"
    :data-placeholder="placeholder"
    @input="onInput"
    @focus="onFocus"
    @blur="onBlur"
    @click="onEditorClick"
    @compositionstart="isComposing = true"
    @compositionend="onCompositionEnd"
    @keydown="onKeyDown"
    @keyup="onKeyUp"
    @mouseup="onMouseUp"
  />
</template>

<style scoped>
.note-text-editor {
  width: 100%;
  min-height: 1.65em;
  margin: 0 0 12px;
  border: none;
  outline: none;
  font-size: 14px;
  line-height: 1.65;
  color: var(--lx-text-body);
  font-family: inherit;
  word-break: break-word;
}

.note-text-editor:empty::before {
  content: attr(data-placeholder);
  color: var(--lx-text-muted);
  pointer-events: none;
}

.note-text-editor :deep(p) {
  margin: 0 0 0.75em;
}

.note-text-editor :deep(p:last-child) {
  margin-bottom: 0;
}

.note-text-editor :deep(h1),
.note-text-editor :deep(h2),
.note-text-editor :deep(h3) {
  margin: 0 0 0.45em;
  font-weight: 600;
  color: var(--lx-text);
}

.note-text-editor :deep(ul),
.note-text-editor :deep(ol) {
  padding-left: 1.5em;
  margin: 0 0 0.75em;
}

.note-text-editor :deep(li) {
  margin-bottom: 0.25em;
}

.note-text-editor :deep(li:has(.note-task-checkbox)) {
  list-style: none;
  margin-left: -1.25em;
}

.note-text-editor :deep(.note-task-checkbox) {
  width: 14px;
  height: 14px;
  margin: 0 8px 0 2px;
  vertical-align: -2px;
  accent-color: var(--lx-accent);
  cursor: pointer;
}

.note-text-editor :deep(strong) {
  font-weight: 700;
}

.note-text-editor :deep(em) {
  font-style: italic;
}

.note-text-editor :deep(u) {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.note-text-editor :deep(s),
.note-text-editor :deep(del),
.note-text-editor :deep(strike) {
  text-decoration: line-through;
  color: var(--lx-text-secondary);
}

.note-text-editor :deep(hr) {
  border: none;
  border-top: 1px solid var(--lx-border-light);
  margin: 1em 0;
}

.note-text-editor :deep(a) {
  color: var(--lx-accent);
}
</style>
