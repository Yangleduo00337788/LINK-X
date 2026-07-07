<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import MainStatusBar from './MainStatusBar.vue'
import Sidebar from './Sidebar.vue'
import ChatList from './ChatList.vue'
import ChatPanel from './ChatPanel.vue'
import ContactsPanel from './ContactsPanel.vue'
import ContactsMainView from './ContactsMainView.vue'
import FavoritesPanel from './FavoritesPanel.vue'
import FilesPanel from './FilesPanel.vue'
import MomentsPanel from './MomentsPanel.vue'
import PlaceholderMainView from './PlaceholderMainView.vue'
import MenuDrawer from './MenuDrawer.vue'
import OverlayHost from './overlay/OverlayHost.vue'
import ChatMoreDrawer from './chat/ChatMoreDrawer.vue'
import GroupInfoDrawer from './chat/GroupInfoDrawer.vue'
import CreateGroupModal from './chat/CreateGroupModal.vue'
import ComprehensiveSearchModal from './chat/ComprehensiveSearchModal.vue'
import VoiceCallModal from './chat/VoiceCallModal.vue'
import VideoCallModal from './chat/VideoCallModal.vue'
import AddGroupMembersModal from './chat/AddGroupMembersModal.vue'
import GroupFilesModal from './chat/GroupFilesModal.vue'
import GroupAlbumModal from './chat/GroupAlbumModal.vue'
import GroupEssenceModal from './chat/GroupEssenceModal.vue'
import GroupAnnouncementModal from './chat/GroupAnnouncementModal.vue'
import ContactProfileModal from './chat/ContactProfileModal.vue'
import SettingsModal from './SettingsModal.vue'
import LockScreen from './LockScreen.vue'
import { useAppState } from '../composables/useAppState'

const { navKey, isLocked } = useAppState()

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

function onDrag(e: MouseEvent) {
  if (!isDragging.value) return
  let newWidth = e.clientX - 52 // 52px 侧边栏宽度
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
    case 'moments':
      return MomentsPanel
    default:
      return ChatList
  }
})

const showChatPanel = computed(() => navKey.value === 'chat')
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
          <ContactsMainView v-else-if="navKey === 'contacts'" />
          <PlaceholderMainView v-else-if="showPlaceholder" :nav="navKey" />
        </main>
      </div>
    </div>

    <!-- 弹窗/抽屉层 -->
    <MenuDrawer />
    <ChatMoreDrawer />
    <GroupInfoDrawer />
    <CreateGroupModal />
    <ComprehensiveSearchModal />
    <VoiceCallModal />
    <VideoCallModal />
    <AddGroupMembersModal />
    <GroupFilesModal />
    <GroupAlbumModal />
    <GroupEssenceModal />
    <GroupAnnouncementModal />
    <ContactProfileModal />
    <SettingsModal />
    <OverlayHost />
    
    <!-- 锁屏层 -->
    <Transition name="fade">
      <LockScreen v-if="isLocked" />
    </Transition>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.app-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #dcdce1;
  border-radius: var(--lx-radius);
  overflow: hidden;
  position: relative;
  transition: background 0.3s ease;
}

.app-shell.is-focused {
  background: rgba(220, 220, 225, 0.75);
  backdrop-filter: blur(40px) saturate(150%);
  -webkit-backdrop-filter: blur(40px) saturate(150%);
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
  background: #ffffff;
  border-radius: var(--lx-radius);
  overflow: hidden;
  --lx-bg-panel: #ffffff; /* 强制功能区和详细区的背景均为白色，融为一体 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  min-width: 0;
}

.col-menu {
  width: 52px;
  min-width: 52px;
  max-width: 52px;
  height: 100%;
  flex-shrink: 0;
}

.col-list {
  height: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.resizer {
  width: 1px;
  background: rgba(0, 0, 0, 0.08);
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
  background: rgba(0, 0, 0, 0.25);
}

.col-chat {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}
</style>