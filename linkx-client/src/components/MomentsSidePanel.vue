<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 友链右侧扩展面板（嵌入主界面 / 独立窗口）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { NIcon, NDropdown, useDialog, type DropdownOption } from 'naive-ui'
import {
  ApertureOutline,
  CloseOutline,
  EllipsisHorizontalOutline,
  OpenOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useMomentsStore, type MomentsPanelTabId } from '../stores/moments'
import { useExtensionDockStore } from '../stores/extensionDock'
import MomentsModal from './MomentsModal.vue'
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
const dialog = useDialog()
const moments = useMomentsStore()
const extensionDock = useExtensionDockStore()
const { panelWidth } = storeToRefs(extensionDock)
const { openTabs, activeTabId } = storeToRefs(moments)

const isPageLayout = computed(() => props.layout === 'page')
const isStandalone = computed(() => props.standalone)
const isDockEmbed = computed(() => props.dockEmbed)
const tabMoreShow = ref(false)
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)

const panelStyle = computed(() =>
  isPageLayout.value || isDockEmbed.value ? undefined : { width: `${panelWidth.value}px` }
)

const tabMoreOptions = computed<DropdownOption[]>(() => [
  { label: t('moments.publishText'), key: 'publishText' },
  { label: t('moments.publishMedia'), key: 'publishMedia' },
  { type: 'divider', key: 'd1' },
  { label: t('moments.closeAllTabs'), key: 'closeAll' }
])

function handleCloseTab(tabId: MomentsPanelTabId) {
  moments.closeTab(tabId)
}

function handleOpenStandalone(tabId: MomentsPanelTabId) {
  const userId = tabId.startsWith('user:') ? tabId.slice(5) : ''
  const tab = openTabs.value.find(item => item.id === tabId)
  if (window.electronAPI?.openMoments) {
    window.electronAPI.openMoments(
      userId ? { userId, name: tab?.title } : undefined
    )
    return
  }
  const base = window.location.href.split('#')[0]
  const hash = userId
    ? `#/moments?userId=${encodeURIComponent(userId)}&name=${encodeURIComponent(tab?.title || '')}`
    : '#/moments'
  window.open(`${base}${hash}`, '_blank', 'noopener')
}

function handleCloseAllTabs() {
  dialog.warning({
    title: t('moments.closeAllTabs'),
    content: t('moments.closeAllTabsConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => {
      moments.closeAllTabs()
    }
  })
}

function onTabMoreSelect(key: string) {
  tabMoreShow.value = false
  if (key === 'publishText') {
    if (window.electronAPI?.openMomentsText) {
      window.electronAPI.openMomentsText()
    }
    return
  }
  if (key === 'publishMedia') {
    if (window.electronAPI?.openMomentsMedia) {
      window.electronAPI.openMomentsMedia()
    }
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
  moments.setPanelWidth(resizeStartWidth.value + delta)
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
    void moments.ensurePanelReady()
    return
  }
  if (!moments.initialized) {
    void moments.fetchMoments()
  }
})

onUnmounted(() => {
  stopResize()
})
</script>

<template>
  <aside
    class="moments-side"
    :class="{
      'moments-side--page': isPageLayout,
      'moments-side--standalone': isStandalone
    }"
    :style="panelStyle"
  >
    <div
      v-if="!isPageLayout && !isDockEmbed"
      class="moments-resizer"
      :class="{ dragging: isResizing }"
      :title="t('moments.resizePanel')"
      @mousedown="startResize"
    />

    <div class="moments-side-body">
      <header v-if="!isDockEmbed && !isStandalone" class="moments-tabbar">
        <button
          v-if="!isStandalone"
          type="button"
          class="moments-tabbar-btn"
          :title="t('moments.collapsePanel')"
          @click="moments.collapsePanel()"
        >
          <svg class="moments-collapse-ico" viewBox="0 0 20 20" aria-hidden="true">
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

        <div v-if="!isStandalone" class="moments-tabbar-divider" aria-hidden="true" />

        <div class="moments-tabbar-tabs">
          <button
            v-for="tab in openTabs"
            :key="tab.id"
            type="button"
            class="moments-tab"
            :class="{ active: tab.id === activeTabId }"
            @click="moments.selectTab(tab.id)"
          >
            <NIcon :component="ApertureOutline" :size="16" />
            <span class="moments-tab-title">{{ tab.title }}</span>
            <span
              class="moments-tab-close"
              role="button"
              tabindex="0"
              :title="t('moments.closeTab')"
              @click.stop="handleCloseTab(tab.id)"
              @keydown.enter.stop.prevent="handleCloseTab(tab.id)"
            >
              <NIcon :component="CloseOutline" :size="14" />
            </span>
            <span
              v-if="!isStandalone"
              class="moments-tab-popout"
              role="button"
              tabindex="0"
              :title="t('moments.openStandalone')"
              @click.stop="handleOpenStandalone(tab.id)"
              @keydown.enter.stop.prevent="handleOpenStandalone(tab.id)"
            >
              <NIcon :component="OpenOutline" :size="13" />
            </span>
          </button>
        </div>

        <div class="moments-tabbar-divider" aria-hidden="true" />

        <n-dropdown
          v-model:show="tabMoreShow"
          trigger="click"
          placement="bottom-end"
          :options="tabMoreOptions"
          @select="onTabMoreSelect"
        >
          <button type="button" class="moments-tabbar-btn" :title="t('common.more')">
            <NIcon :component="EllipsisHorizontalOutline" :size="18" />
          </button>
        </n-dropdown>
      </header>

      <div
        class="moments-side-main"
        :class="{ 'moments-standalone-column': isStandalone }"
      >
        <MomentsModal :embedded="!isStandalone" />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.moments-side {
  position: relative;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.moments-side--page {
  flex: 1;
  width: 100%;
  min-width: 0;
  border-left: none;
}

.moments-resizer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 2;
}

.moments-resizer.dragging {
  background: var(--lx-accent-soft);
}

.moments-side-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
}

.moments-tabbar {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  min-height: 40px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

.moments-tabbar-btn {
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

.moments-tabbar-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.moments-collapse-ico {
  width: 18px;
  height: 18px;
  display: block;
}

.moments-tabbar-divider {
  width: 1px;
  align-self: stretch;
  background: var(--lx-border-light);
  flex-shrink: 0;
}

.moments-tabbar-tabs {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  overflow-x: auto;
  scrollbar-width: none;
}

.moments-tabbar-tabs::-webkit-scrollbar {
  display: none;
}

.moments-tab {
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

.moments-tab:hover,
.moments-tab.active {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.moments-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.moments-tab-close,
.moments-tab-popout {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-muted);
}

.moments-tab-close:hover,
.moments-tab-popout:hover {
  background: var(--lx-bg-panel-deep, var(--lx-bg-hover));
  color: var(--lx-text-body);
}

.moments-side-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.moments-standalone-column {
  width: min(100%, 720px);
  margin-left: auto;
  margin-right: auto;
  box-sizing: border-box;
}

.moments-side--standalone .moments-side-main {
  flex: 1;
  min-height: 0;
}

.moments-side-main :deep(.moments-wrapper.embedded) {
  height: 100%;
  min-height: 0;
}
</style>
