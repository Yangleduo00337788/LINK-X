<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 主界面右侧统一扩展标签坞：灵伴 / 友链等标签合并展示，点击切换。
 */
import { computed, onUnmounted, ref } from 'vue'
import { NIcon, NDropdown, useDialog, type DropdownOption } from 'naive-ui'
import {
  ApertureOutline,
  CloseOutline,
  DocumentTextOutline,
  EllipsisHorizontalOutline,
  OpenOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useExtensionDockStore, type ExtensionTabKey } from '../stores/extensionDock'
import { useLinkMateStore } from '../stores/linkmate'
import { useMomentsStore, type MomentsPanelTabId } from '../stores/moments'
import { useNoteStore, type NotePanelTabId } from '../stores/note'
import LinkMateSidePanel from './LinkMateSidePanel.vue'
import MomentsSidePanel from './MomentsSidePanel.vue'
import NotesSidePanel from './NotesSidePanel.vue'
import LinkMateLogoMark from './LinkMateLogoMark.vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const dialog = useDialog()
const dock = useExtensionDockStore()
const linkMate = useLinkMateStore()
const moments = useMomentsStore()
const notes = useNoteStore()
const { panelWidth, activeTabKey, activeKind, allTabs } = storeToRefs(dock)
const { streaming } = storeToRefs(linkMate)

const tabMoreShow = ref(false)
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)

const tabMoreOptions = computed<DropdownOption[]>(() => {
  if (activeKind.value === 'moments') {
    return [
      { label: t('moments.publishText'), key: 'publishText' },
      { label: t('moments.publishMedia'), key: 'publishMedia' },
      { type: 'divider', key: 'd1' },
      { label: t('moments.closeAllTabs'), key: 'closeAll' }
    ]
  }
  if (activeKind.value === 'notes') {
    return [
      { label: t('noteEditor.newNote'), key: 'newNote' },
      { label: t('noteEditor.saveNote'), key: 'saveNote' },
      { type: 'divider', key: 'd1' },
      { label: t('notes.closeAllTabs'), key: 'closeAll' }
    ]
  }
  return [
    { label: t('linkmate.newChat'), key: 'newChat' },
    { label: t('linkmate.openHistory'), key: 'history' },
    { type: 'divider', key: 'd1' },
    { label: t('linkmate.closeAllTabs'), key: 'closeAll' }
  ]
})

const panelStyle = computed(() => ({
  width: `${panelWidth.value}px`,
  flex: `0 0 ${panelWidth.value}px`
}))

function handleCloseTab(key: ExtensionTabKey) {
  if (key.startsWith('linkmate:')) {
    void linkMate.closeTab(key.slice('linkmate:'.length))
    return
  }
  if (key.startsWith('moments:')) {
    moments.closeTab(key.slice('moments:'.length) as MomentsPanelTabId)
    return
  }
  void notes.closeTab(key.slice('notes:'.length) as NotePanelTabId)
}

function handleOpenStandalone(key: ExtensionTabKey) {
  if (key.startsWith('linkmate:')) {
    const sessionId = key.slice('linkmate:'.length)
    if (window.electronAPI?.openLinkMate) {
      window.electronAPI.openLinkMate(sessionId)
      return
    }
    const base = window.location.href.split('#')[0]
    window.open(`${base}#/linkmate/${encodeURIComponent(sessionId)}`, '_blank', 'noopener')
    return
  }
  if (key.startsWith('moments:')) {
    const tabId = key.slice('moments:'.length) as MomentsPanelTabId
    const userId = tabId.startsWith('user:') ? tabId.slice(5) : ''
    const tab = moments.openTabs.find(item => item.id === tabId)
    if (window.electronAPI?.openMoments) {
      window.electronAPI.openMoments(userId ? { userId, name: tab?.title } : undefined)
      return
    }
    const base = window.location.href.split('#')[0]
    const hash = userId
      ? `#/moments?userId=${encodeURIComponent(userId)}&name=${encodeURIComponent(tab?.title || '')}`
      : '#/moments'
    window.open(`${base}${hash}`, '_blank', 'noopener')
    return
  }
  const noteTabId = key.slice('notes:'.length) as NotePanelTabId
  const noteId = noteTabId === 'new' ? undefined : noteTabId
  if (window.electronAPI?.openNotes) {
    window.electronAPI.openNotes(noteId)
    return
  }
  const base = window.location.href.split('#')[0]
  const hash = noteId ? `#/notes/${encodeURIComponent(noteId)}` : '#/notes'
  window.open(`${base}${hash}`, '_blank', 'noopener')
}

function handleCloseAllTabs() {
  const title =
    activeKind.value === 'moments'
      ? t('moments.closeAllTabs')
      : activeKind.value === 'notes'
        ? t('notes.closeAllTabs')
        : t('linkmate.closeAllTabs')
  const content =
    activeKind.value === 'moments'
      ? t('moments.closeAllTabsConfirm')
      : activeKind.value === 'notes'
        ? t('notes.closeAllTabsConfirm')
        : t('linkmate.closeAllTabsConfirm')
  dialog.warning({
    title,
    content,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => {
      if (activeKind.value === 'moments') {
        moments.closeAllTabs()
      } else if (activeKind.value === 'notes') {
        void notes.save().then(() => notes.closeAllTabs())
      } else {
        void linkMate.closeAllTabs()
      }
    }
  })
}

function onTabMoreSelect(key: string) {
  tabMoreShow.value = false
  if (key === 'publishText') {
    window.electronAPI?.openMomentsText?.()
    return
  }
  if (key === 'publishMedia') {
    window.electronAPI?.openMomentsMedia?.()
    return
  }
  if (key === 'newChat') {
    void linkMate.startNewChat()
    return
  }
  if (key === 'newNote') {
    notes.openNewTab()
    return
  }
  if (key === 'saveNote') {
    void notes.save()
    return
  }
  if (key === 'history') {
    linkMate.showHistory = true
    return
  }
  if (key === 'closeAll') {
    handleCloseAllTabs()
  }
}

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
  dock.setPanelWidth(resizeStartWidth.value + delta)
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onUnmounted(() => {
  stopResize()
})
</script>

<template>
  <aside class="extension-dock" :style="panelStyle">
    <div
      class="extension-resizer"
      :class="{ dragging: isResizing }"
      :title="t('extensionDock.resizePanel')"
      @mousedown="startResize"
    />

    <div class="extension-dock-body">
      <header class="extension-tabbar">
        <button
          type="button"
          class="extension-tabbar-btn"
          :title="t('extensionDock.collapsePanel')"
          :disabled="streaming"
          @click="dock.collapsePanel()"
        >
          <svg class="extension-collapse-ico" viewBox="0 0 20 20" aria-hidden="true">
            <rect x="3.5" y="3.5" width="13" height="13" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
            <path
              d="M12.5 7.5 L9.5 10.5 M9.5 10.5 H12.5 M9.5 10.5 V7.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>

        <div class="extension-tabbar-divider" aria-hidden="true" />

        <div class="extension-tabbar-tabs">
          <button
            v-for="tab in allTabs"
            :key="tab.key"
            type="button"
            class="extension-tab"
            :class="{ active: tab.key === activeTabKey }"
            @click="dock.selectTab(tab.key)"
          >
            <LinkMateLogoMark v-if="tab.kind === 'linkmate'" size="sm" />
            <NIcon v-else-if="tab.kind === 'moments'" :component="ApertureOutline" :size="16" />
            <NIcon v-else :component="DocumentTextOutline" :size="16" />
            <span class="extension-tab-title">{{ tab.title }}</span>
            <span
              class="extension-tab-close"
              role="button"
              tabindex="0"
              :title="t('extensionDock.closeTab')"
              @click.stop="handleCloseTab(tab.key)"
              @keydown.enter.stop.prevent="handleCloseTab(tab.key)"
            >
              <NIcon :component="CloseOutline" :size="14" />
            </span>
            <span
              class="extension-tab-popout"
              role="button"
              tabindex="0"
              :title="t('extensionDock.openStandalone')"
              @click.stop="handleOpenStandalone(tab.key)"
              @keydown.enter.stop.prevent="handleOpenStandalone(tab.key)"
            >
              <NIcon :component="OpenOutline" :size="13" />
            </span>
          </button>
        </div>

        <div class="extension-tabbar-divider" aria-hidden="true" />

        <n-dropdown
          v-model:show="tabMoreShow"
          trigger="click"
          placement="bottom-end"
          :options="tabMoreOptions"
          @select="onTabMoreSelect"
        >
          <button type="button" class="extension-tabbar-btn" :title="t('common.more')">
            <NIcon :component="EllipsisHorizontalOutline" :size="18" />
          </button>
        </n-dropdown>
      </header>

      <div class="extension-dock-content">
        <LinkMateSidePanel
          v-if="activeKind === 'linkmate'"
          layout="side"
          dock-embed
        />
        <MomentsSidePanel
          v-if="activeKind === 'moments'"
          layout="side"
          dock-embed
        />
        <NotesSidePanel
          v-if="activeKind === 'notes'"
          layout="side"
          dock-embed
        />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.extension-dock {
  position: relative;
  flex: 0 0 auto;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
}

.extension-resizer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 2;
}

.extension-resizer.dragging {
  background: var(--lx-accent-soft);
}

.extension-dock-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.extension-tabbar {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  min-height: 40px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

.extension-tabbar-btn {
  flex-shrink: 0;
  width: 40px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.extension-tabbar-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.extension-tabbar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.extension-collapse-ico {
  width: 18px;
  height: 18px;
  display: block;
}

.extension-tabbar-divider {
  width: 1px;
  align-self: stretch;
  background: var(--lx-border-light);
  flex-shrink: 0;
}

.extension-tabbar-tabs {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  overflow-x: auto;
  scrollbar-width: none;
}

.extension-tabbar-tabs::-webkit-scrollbar {
  display: none;
}

.extension-tab {
  flex-shrink: 0;
  max-width: 200px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px 0 12px;
  font-size: var(--lx-font-md);
}

.extension-tab:hover,
.extension-tab.active {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.extension-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.extension-tab-close,
.extension-tab-popout {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-muted);
}

.extension-tab-close:hover,
.extension-tab-popout:hover {
  background: var(--lx-bg-panel-deep, var(--lx-bg-hover));
  color: var(--lx-text-body);
}

.extension-dock-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}

.extension-dock-content :deep(.linkmate-side),
.extension-dock-content :deep(.moments-side),
.extension-dock-content :deep(.notes-side) {
  position: absolute;
  inset: 0;
  width: 100% !important;
  max-width: none !important;
  border-left: none;
}
</style>
