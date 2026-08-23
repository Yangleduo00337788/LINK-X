<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 短视频右侧扩展面板（嵌入主界面 / 独立窗口）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { NIcon, NDropdown, useDialog, type DropdownOption } from 'naive-ui'
import {
  CloseOutline,
  EllipsisHorizontalOutline,
  FilmOutline,
  OpenOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useShortVideoStore, type ShortVideoPanelTabId } from '../stores/shortVideo'
import { useExtensionDockStore } from '../stores/extensionDock'
import ShortVideoMainView from './ShortVideoMainView.vue'
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
const shortVideo = useShortVideoStore()
const extensionDock = useExtensionDockStore()
const { panelWidth } = storeToRefs(extensionDock)
const { openTabs, activeTabId } = storeToRefs(shortVideo)

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
  { type: 'divider', key: 'd1' },
  { label: t('shortVideo.closeAllTabs'), key: 'closeAll' }
])

function handleCloseTab(tabId: ShortVideoPanelTabId) {
  shortVideo.closeTab(tabId)
}

function handleOpenStandalone(_tabId: ShortVideoPanelTabId) {
  if (window.electronAPI?.openShortVideo) {
    window.electronAPI.openShortVideo()
    return
  }
  const base = window.location.href.split('#')[0]
  window.open(`${base}#/short-video`, '_blank', 'noopener')
}

function handleCloseAllTabs() {
  dialog.warning({
    title: t('shortVideo.closeAllTabs'),
    content: t('shortVideo.closeAllTabsConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => {
      shortVideo.closeAllTabs()
    }
  })
}

function onTabMoreSelect(key: string) {
  tabMoreShow.value = false
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
  shortVideo.setPanelWidth(resizeStartWidth.value + delta)
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
    void shortVideo.ensurePanelReady()
    return
  }
  if (!shortVideo.initialized) {
    void shortVideo.fetchFeed(true)
  }
})

onUnmounted(() => {
  stopResize()
})
</script>

<template>
  <aside
    class="short-video-side"
    :class="{
      'short-video-side--page': isPageLayout,
      'short-video-side--standalone': isStandalone
    }"
    :style="panelStyle"
  >
    <div
      v-if="!isPageLayout && !isDockEmbed"
      class="short-video-resizer"
      :class="{ dragging: isResizing }"
      :title="t('shortVideo.resizePanel')"
      @mousedown="startResize"
    />

    <div class="short-video-side-body">
      <header v-if="!isDockEmbed && !isStandalone" class="short-video-tabbar">
        <button
          type="button"
          class="short-video-tabbar-btn"
          :title="t('shortVideo.collapsePanel')"
          @click="shortVideo.collapsePanel()"
        >
          <svg class="short-video-collapse-ico" viewBox="0 0 20 20" aria-hidden="true">
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

        <div class="short-video-tabbar-divider" aria-hidden="true" />

        <div class="short-video-tabbar-tabs">
          <button
            v-for="tab in openTabs"
            :key="tab.id"
            type="button"
            class="short-video-tab"
            :class="{ active: tab.id === activeTabId }"
            @click="shortVideo.selectTab(tab.id)"
          >
            <NIcon :component="FilmOutline" :size="16" />
            <span class="short-video-tab-title">{{ tab.title }}</span>
            <span
              class="short-video-tab-close"
              role="button"
              tabindex="0"
              :title="t('shortVideo.closeTab')"
              @click.stop="handleCloseTab(tab.id)"
              @keydown.enter.stop.prevent="handleCloseTab(tab.id)"
            >
              <NIcon :component="CloseOutline" :size="14" />
            </span>
            <span
              class="short-video-tab-popout"
              role="button"
              tabindex="0"
              :title="t('shortVideo.openStandalone')"
              @click.stop="handleOpenStandalone(tab.id)"
              @keydown.enter.stop.prevent="handleOpenStandalone(tab.id)"
            >
              <NIcon :component="OpenOutline" :size="13" />
            </span>
          </button>
        </div>

        <div class="short-video-tabbar-divider" aria-hidden="true" />

        <n-dropdown
          v-model:show="tabMoreShow"
          trigger="click"
          placement="bottom-end"
          :options="tabMoreOptions"
          @select="onTabMoreSelect"
        >
          <button type="button" class="short-video-tabbar-btn" :title="t('common.more')">
            <NIcon :component="EllipsisHorizontalOutline" :size="18" />
          </button>
        </n-dropdown>
      </header>

      <div class="short-video-side-main">
        <ShortVideoMainView />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.short-video-side {
  position: relative;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.short-video-side--page {
  flex: 1;
  width: 100%;
  min-width: 0;
  border-left: none;
}

.short-video-resizer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 2;
}

.short-video-resizer.dragging {
  background: var(--lx-accent-soft);
}

.short-video-side-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
}

.short-video-tabbar {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  min-height: 40px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
}

.short-video-tabbar-btn {
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

.short-video-tabbar-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.short-video-tabbar-divider {
  width: 1px;
  background: var(--lx-border-light);
  flex-shrink: 0;
}

.short-video-tabbar-tabs {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  overflow-x: auto;
}

.short-video-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  max-width: 180px;
  flex-shrink: 0;
}

.short-video-tab.active {
  color: var(--lx-text-body);
  background: var(--lx-bg-panel);
  box-shadow: inset 0 -2px 0 var(--lx-accent);
}

.short-video-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.short-video-tab-close,
.short-video-tab-popout {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  opacity: 0.7;
}

.short-video-tab-close:hover,
.short-video-tab-popout:hover {
  opacity: 1;
  background: var(--lx-bg-hover);
}

.short-video-side-main {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.short-video-side-main :deep(.short-video-main) {
  flex: 1;
  min-height: 0;
  height: 100%;
  width: 100%;
}

.short-video-side--page .short-video-side-main,
.short-video-side--standalone .short-video-side-main {
  height: 100%;
}
</style>
