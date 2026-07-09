<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import MainStatusBar from './MainStatusBar.vue'
import Sidebar from './Sidebar.vue'
import ChatList from './ChatList.vue'
import ChatPanel from './ChatPanel.vue'
import ContactsPanel from './ContactsPanel.vue'
import ContactsMainView from './ContactsMainView.vue'
import FavoritesPanel from './FavoritesPanel.vue'
import FilesPanel from './FilesPanel.vue'
import MomentsPanel from './MomentsPanel.vue'
import AppsPanel from './AppsPanel.vue'
import CalendarPanel from './CalendarPanel.vue'
import CalendarMainView from './CalendarMainView.vue'
import PlaceholderMainView from './PlaceholderMainView.vue'
import MediaNowPlayingBar from './MediaNowPlayingBar.vue'
import OverlayHost from './overlay/OverlayHost.vue'

const CreateGroupModal = defineAsyncComponent(() => import('./chat/CreateGroupModal.vue'))
const ComprehensiveSearchModal = defineAsyncComponent(() => import('./chat/ComprehensiveSearchModal.vue'))
const VoiceCallModal = defineAsyncComponent(() => import('./chat/VoiceCallModal.vue'))
const VideoCallModal = defineAsyncComponent(() => import('./chat/VideoCallModal.vue'))
const AddGroupMembersModal = defineAsyncComponent(() => import('./chat/AddGroupMembersModal.vue'))
const GroupFilesModal = defineAsyncComponent(() => import('./chat/GroupFilesModal.vue'))
const GroupAlbumModal = defineAsyncComponent(() => import('./chat/GroupAlbumModal.vue'))
const GroupEssenceModal = defineAsyncComponent(() => import('./chat/GroupEssenceModal.vue'))
const GroupAnnouncementModal = defineAsyncComponent(() => import('./chat/GroupAnnouncementModal.vue'))
const RedPacketModal = defineAsyncComponent(() => import('./chat/RedPacketModal.vue'))
const RedPacketReceiveModal = defineAsyncComponent(() => import('./chat/RedPacketReceiveModal.vue'))
const ContactProfileModal = defineAsyncComponent(() => import('./chat/ContactProfileModal.vue'))
const MomentsModal = defineAsyncComponent(() => import('./MomentsModal.vue'))
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'

const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const { navKey } = storeToRefs(appStore)
const { momentsModalOpen } = storeToRefs(chatModalsStore)

const isElectron = !!window.electronAPI

const listWidth = ref(260)
const isDragging = ref(false)
const isWindowFocused = ref(document.hasFocus())

function onWindowFocus() {
  isWindowFocused.value = true
}

function onWindowBlur() {
  isWindowFocused.value = false
}

function startDrag() {
  isDragging.value = true
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const SIDEBAR_WIDTH = 64

function onDrag(e: MouseEvent) {
  if (!isDragging.value) return
  let newWidth = e.clientX - SIDEBAR_WIDTH
  if (newWidth < 200) newWidth = 200
  if (newWidth > 500) newWidth = 500
  listWidth.value = newWidth
}

function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  window.addEventListener('focus', onWindowFocus)
  window.addEventListener('blur', onWindowBlur)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  window.removeEventListener('focus', onWindowFocus)
  window.removeEventListener('blur', onWindowBlur)
})

const middleComponent = computed(() => {
  switch (navKey.value) {
    case 'chat':
      return ChatList
    case 'contacts':
      return ContactsPanel
    case 'favorites':
      return FavoritesPanel
    case 'files':
      return FilesPanel
    case 'calendar':
      return CalendarPanel
    case 'moments':
      return MomentsPanel
    case 'apps':
      return AppsPanel
    default:
      return ChatList
  }
})

const showChatPanel = computed(() => navKey.value === 'chat')
const showCalendarMain = computed(() => navKey.value === 'calendar')
const showPlaceholder = computed(() =>
  ['contacts', 'favorites', 'files', 'moments', 'apps'].includes(navKey.value)
)
</script>

<template>
  <div class="app-shell" :class="{ 'is-focused': isWindowFocused }">
    <header class="top-status">
      <MainStatusBar variant="profile" />
    </header>

    <div class="main-panel func-card">
      <aside class="col-menu">
        <Sidebar />
      </aside>
      <div class="content-wrapper">
        <section
          class="col-list"
          :style="{ width: listWidth + 'px', minWidth: listWidth + 'px', maxWidth: listWidth + 'px' }"
        >
          <component :is="middleComponent" />
        </section>
        <div class="resizer" @mousedown="startDrag" :class="{ dragging: isDragging }"></div>
        <main class="col-chat">
          <!-- 右侧主内容区域（动态组件） -->
          <ChatPanel v-if="showChatPanel" />
          <CalendarMainView v-else-if="showCalendarMain" />
          <ContactsMainView v-else-if="navKey === 'contacts'" />
          <PlaceholderMainView v-else-if="showPlaceholder" :nav="navKey" />
        </main>
      </div>
    </div>

    <MediaNowPlayingBar />

    <!-- 弹窗/抽屉层 -->
    <CreateGroupModal />
    <ComprehensiveSearchModal />
    <VoiceCallModal />
    <VideoCallModal />
    <AddGroupMembersModal />
    <GroupFilesModal />
    <GroupAlbumModal />
    <GroupEssenceModal />
    <GroupAnnouncementModal />
    <RedPacketModal />
    <RedPacketReceiveModal />
    <ContactProfileModal />
    <div v-if="momentsModalOpen && !isElectron" class="moments-modal-backdrop">
      <MomentsModal />
    </div>
    <OverlayHost />
  </div>
</template>

<style scoped>
.app-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
  border-radius: var(--lx-radius);
  overflow: hidden;
  position: relative;
}

.app-shell.is-focused {
  /* 使用 Windows 官方 Mica 材质，不再使用 CSS 模拟 */
  background: transparent;
}

.top-status {
  flex-shrink: 0;
  height: 40px;
  min-height: 40px;
  width: 100%;
  z-index: 2;
  position: relative;
  background: transparent;
}

.main-panel {
  flex: 1;
  min-height: 0;
  margin: 0;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
  padding: 0 8px 8px 0;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: row;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: var(--lx-shadow-soft);
  min-width: 0;
}

.col-menu {
  width: var(--lx-sidebar-width);
  min-width: var(--lx-sidebar-width);
  max-width: var(--lx-sidebar-width);
  height: 100%;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
}

.col-list {
  height: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--lx-bg-list);
  --lx-bg-panel: var(--lx-bg-list);
}

.resizer {
  width: 1px;
  background: transparent;
  cursor: col-resize;
  position: relative;
  z-index: 10;
  transition: background 0.2s;
}

.resizer::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -3px;
  right: -3px;
  cursor: col-resize;
}

.resizer:hover,
.resizer.dragging {
  background: var(--lx-separator-fade, rgba(0, 0, 0, 0.06));
}

.col-chat {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--lx-bg-card);
  --lx-bg-panel: var(--lx-bg-card);
}

.moments-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2100;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>