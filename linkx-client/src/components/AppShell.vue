<script setup lang="ts">
/**
 * 应用主壳层组件。
 * <p>
 * 三栏布局：左侧导航、中间列表、右侧主内容区；
 * 负责导航切换、列表宽度拖拽、窗口焦点状态及全局弹窗挂载。
 * </p>
 */
// Vue 组合式 API：计算属性、响应式、生命周期、异步组件
import { computed, ref, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
// 顶部状态栏
import MainStatusBar from './MainStatusBar.vue'
// 左侧导航栏
import Sidebar from './Sidebar.vue'
// 聊天会话列表
import ChatList from './ChatList.vue'
// 聊天主面板
import ChatPanel from './ChatPanel.vue'
// 联系人列表面板
import ContactsPanel from './ContactsPanel.vue'
// 联系人右侧主视图
import ContactsMainView from './ContactsMainView.vue'
// 收藏面板
import FavoritesPanel from './FavoritesPanel.vue'
// 文件面板
import FilesPanel from './FilesPanel.vue'
// 友链面板
import MomentsPanel from './MomentsPanel.vue'
// 应用面板
import AppsPanel from './AppsPanel.vue'
// 日历列表面板
import CalendarPanel from './CalendarPanel.vue'
// 日历主视图
import CalendarMainView from './CalendarMainView.vue'
// 通用占位主视图
import PlaceholderMainView from './PlaceholderMainView.vue'
// 媒体播放条
import MediaNowPlayingBar from './MediaNowPlayingBar.vue'
// 全屏 Overlay 宿主
import OverlayHost from './overlay/OverlayHost.vue'

// 以下弹窗异步懒加载，减小首屏包体积
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
const EditProfileModal = defineAsyncComponent(() => import('./EditProfileModal.vue'))
const MomentsModal = defineAsyncComponent(() => import('./MomentsModal.vue'))
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../stores/chatModals'

// 获取应用 Store 实例
const appStore = useAppStore()
// 获取聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 解构当前导航键
const { navKey } = storeToRefs(appStore)
// 解构友链弹窗打开状态
const { momentsModalOpen } = storeToRefs(chatModalsStore)

// 是否在 Electron 环境中运行
const isElectron = !!window.electronAPI

// 中间列表列宽度（可拖拽调整）
const listWidth = ref(260)
// 是否正在拖拽调整列宽
const isDragging = ref(false)
// 窗口是否获得焦点（用于 Mica 等原生材质效果）
const isWindowFocused = ref(document.hasFocus())

// 窗口获得焦点时更新状态
function onWindowFocus() {
  isWindowFocused.value = true
}

// 窗口失去焦点时更新状态
function onWindowBlur() {
  isWindowFocused.value = false
}

// 开始拖拽调整中间列宽度
function startDrag() {
  isDragging.value = true
  document.addEventListener('mousemove', onDrag) // 监听鼠标移动
  document.addEventListener('mouseup', stopDrag) // 监听鼠标释放
  document.body.style.cursor = 'col-resize' // 全局光标变为列调整
  document.body.style.userSelect = 'none' // 禁止文本选中
}

// 左侧 Sidebar 固定宽度（拖拽计算时需减去）
const SIDEBAR_WIDTH = 64

// 拖拽过程中更新列表列宽度，限制在 200~500px
function onDrag(e: MouseEvent) {
  if (!isDragging.value) return
  let newWidth = e.clientX - SIDEBAR_WIDTH // 鼠标 X 减去侧栏宽度
  if (newWidth < 200) newWidth = 200 // 最小宽度
  if (newWidth > 500) newWidth = 500 // 最大宽度
  listWidth.value = newWidth
}

// 结束拖拽，移除监听并恢复样式
function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

// 挂载时注册窗口焦点/失焦监听
onMounted(() => {
  window.addEventListener('focus', onWindowFocus)
  window.addEventListener('blur', onWindowBlur)
})

// 卸载时清理拖拽与焦点监听
onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  window.removeEventListener('focus', onWindowFocus)
  window.removeEventListener('blur', onWindowBlur)
})

// 根据当前导航键计算中间列应渲染的组件
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
      return ChatList // 未知导航默认消息列表
  }
})

// 是否显示聊天主面板（消息导航）
const showChatPanel = computed(() => navKey.value === 'chat')
// 是否显示日历主视图
const showCalendarMain = computed(() => navKey.value === 'calendar')
// 是否显示占位主视图（联系人/收藏/文件/友链/应用）
const showPlaceholder = computed(() =>
  ['contacts', 'favorites', 'files', 'moments', 'apps'].includes(navKey.value)
)
</script>

<template>
  <!-- 应用壳层根容器，焦点态用于原生材质 -->
  <div class="app-shell" :class="{ 'is-focused': isWindowFocused }">
    <!-- 顶部状态栏 -->
    <header class="top-status">
      <MainStatusBar variant="profile" />
    </header>

    <!-- 主面板：侧栏 + 内容区 -->
    <div class="main-panel func-card">
      <!-- 左侧导航菜单列 -->
      <aside class="col-menu">
        <Sidebar />
      </aside>
      <div class="content-wrapper">
        <!-- 中间列表列（宽度可拖拽） -->
        <section
          class="col-list"
          :style="{ width: listWidth + 'px', minWidth: listWidth + 'px', maxWidth: listWidth + 'px' }"
        >
          <component :is="middleComponent" />
        </section>
        <!-- 列宽拖拽分隔条 -->
        <div class="resizer" @mousedown="startDrag" :class="{ dragging: isDragging }"></div>
        <!-- 右侧主内容区 -->
        <main class="col-chat">
          <!-- 右侧主内容区域（动态组件） -->
          <ChatPanel v-if="showChatPanel" />
          <CalendarMainView v-else-if="showCalendarMain" />
          <ContactsMainView v-else-if="navKey === 'contacts'" />
          <PlaceholderMainView v-else-if="showPlaceholder" :nav="navKey" />
        </main>
      </div>
    </div>

    <!-- 底部媒体播放条 -->
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
    <EditProfileModal />
    <!-- 浏览器环境下友链全屏弹窗 -->
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
