<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 笔记右侧扩展面板（嵌入主界面 / 独立窗口）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useNoteStore } from '../stores/note'
import { useExtensionDockStore } from '../stores/extensionDock'
import NoteEditor from './NoteEditor.vue'
import { useI18n } from '../i18n'

const props = withDefaults(
  defineProps<{
    layout?: 'page' | 'side'
    standalone?: boolean
    dockEmbed?: boolean
  }>(),
  {
    layout: 'page',
    standalone: false,
    dockEmbed: false
  }
)

const { t } = useI18n()
const noteStore = useNoteStore()
const extensionDock = useExtensionDockStore()
const { panelWidth } = storeToRefs(extensionDock)

const isPageLayout = computed(() => props.layout === 'page')
const isDockEmbed = computed(() => props.dockEmbed)

const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)

const panelStyle = computed(() =>
  isPageLayout.value || isDockEmbed.value ? undefined : { width: `${panelWidth.value}px` }
)

function startResize(e: MouseEvent) {
  isResizing.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = panelWidth.value
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onResize(e: MouseEvent) {
  if (!isResizing.value) return
  const delta = resizeStartX.value - e.clientX
  noteStore.setPanelWidth(resizeStartWidth.value + delta)
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  if (!isDockEmbed.value) {
    void noteStore.ensurePanelReady()
    return
  }
  if (!noteStore.initialized) {
    void noteStore.fetchNotes()
  }
})

onUnmounted(() => {
  stopResize()
})
</script>

<template>
  <aside
    class="notes-side"
    :class="{
      'notes-side--page': isPageLayout,
      'notes-side--standalone': standalone
    }"
    :style="panelStyle"
  >
    <div
      v-if="!isPageLayout && !isDockEmbed"
      class="notes-resizer"
      :class="{ dragging: isResizing }"
      :title="t('notes.resizePanel')"
      @mousedown="startResize"
    />

    <div class="notes-side-body">
      <NoteEditor embedded :standalone="standalone" :dock-embed="dockEmbed" />
    </div>
  </aside>
</template>

<style scoped>
.notes-side {
  position: relative;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
}

.notes-side--page {
  width: 100%;
  height: 100%;
  max-width: none;
  border-left: none;
}

.notes-resizer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 2;
}

.notes-resizer.dragging {
  background: var(--lx-accent-soft);
}

.notes-side-body {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
