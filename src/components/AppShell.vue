<script setup lang="ts">
import { computed } from 'vue'
import MainStatusBar from './MainStatusBar.vue'
import Sidebar from './Sidebar.vue'
import ChatList from './ChatList.vue'
import ChatPanel from './ChatPanel.vue'
import ContactsPanel from './ContactsPanel.vue'
import ContactsMainView from './ContactsMainView.vue'
import FavoritesPanel from './FavoritesPanel.vue'
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
import SettingsModal from './SettingsModal.vue'
import { useAppState } from '../composables/useAppState'

const { navKey } = useAppState()

const middleComponent = computed(() => {
  switch (navKey.value) {
    case 'chat':
      return ChatList
    case 'contacts':
      return ContactsPanel
    case 'favorites':
      return FavoritesPanel
    case 'moments':
      return MomentsPanel
    default:
      return ChatList
  }
})

const showChatPanel = computed(() => navKey.value === 'chat')
const showPlaceholder = computed(() =>
  ['contacts', 'favorites', 'moments', 'apps'].includes(navKey.value)
)
</script>

<template>
  <div class="app-shell">
    <header class="top-status liquid-glass-bar">
      <MainStatusBar variant="profile" weather-text="阴" />
    </header>

    <div class="main-panel func-card">
      <aside class="col-menu">
        <Sidebar />
      </aside>
      <div class="panel-divider" aria-hidden="true" />
      <section class="col-list">
        <component :is="middleComponent" />
      </section>
      <div class="panel-divider" aria-hidden="true" />
      <main class="col-chat">
        <!-- 右侧主内容区域（动态组件） -->
        <ChatPanel v-if="showChatPanel" />
        <ContactsMainView v-else-if="navKey === 'contacts'" />
        <PlaceholderMainView v-else-if="showPlaceholder" :nav="navKey" />
      </main>
    </div>

    <MenuDrawer />
    <!-- 弹窗/抽屉统一容器（仅在此渲染主窗口弹窗） -->
    <OverlayHost />
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
    <SettingsModal />
  </div>
</template>

<style scoped>
.app-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.top-status {
  flex-shrink: 0;
  height: 40px;
  min-height: 40px;
  width: 100%;
  z-index: 2;
  position: relative;
  background: rgba(232, 232, 232, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
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
  background: var(--lx-bg-window, #e8e8e8);
}

.panel-divider {
  width: 1px;
  flex-shrink: 0;
  align-self: stretch;
  background: linear-gradient(
    180deg,
    transparent 0%,
    var(--lx-divider, #e0e0e0) 8%,
    var(--lx-divider, #e0e0e0) 92%,
    transparent 100%
  );
  opacity: 0.85;
}

.col-menu {
  width: 58px;
  min-width: 58px;
  max-width: 58px;
  height: 100%;
  flex-shrink: 0;
}

.col-list {
  width: 259px;
  min-width: 259px;
  max-width: 259px;
  height: 100%;
  flex-shrink: 0;
  overflow: hidden;
}

.col-chat {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}
</style>