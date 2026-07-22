<script setup lang="ts">
/**
 * 左侧导航栏组件。
 * <p>
 * 提供主导航切换（消息、联系人、收藏等）、个人头像入口、
 * 主题快捷入口与更多菜单（设置、锁定、退出等）。
 * </p>
 */
// Vue 渲染函数、nextTick 与组件类型
import { h, nextTick, ref, computed, type Component } from 'vue'
import { NIcon, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import {
  ChatbubbleEllipsesOutline,
  PersonOutline,
  ApertureOutline,
  MenuOutline,
  BookmarkOutline,
  FolderOutline,
  CalendarOutline,
  ColorPaletteOutline,
  TimeOutline,
  RefreshOutline,
  HelpCircleOutline,
  LockClosedOutline,
  SettingsOutline,
  LogOutOutline
} from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import { storeToRefs } from 'pinia'
import { generateDefaultAvatar } from '../utils/defaultAvatar'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { useSettingsStore } from '../stores/settings'
import { useOverlayStore } from '../stores/overlay'
import { useFavoritesStore } from '../stores/favorites'
import { useDriveStore } from '../stores/drive'
import { useContactsStore } from '../stores/contacts'
import { useMomentsStore } from '../stores/moments'
import { useCalendarStore } from '../stores/calendar'
import { useNotificationsStore } from '../stores/notifications'
import type { NavKey } from '../types'
import { useI18n } from '../i18n'

// 获取各 Store 实例
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const settingsStore = useSettingsStore()
const overlayStore = useOverlayStore()
const favoritesStore = useFavoritesStore()
const driveStore = useDriveStore()
const contactsStore = useContactsStore()
const momentsStore = useMomentsStore()
const calendarStore = useCalendarStore()
const notificationsStore = useNotificationsStore()
const { t } = useI18n()
// 解构导航键、用户资料、已保存登录信息、会话列表
const { navKey, userProfile, savedLogin, sessions } = storeToRefs(appStore)
const { calendarRemindUnreadCount, contactsBadgeCount } = storeToRefs(notificationsStore)
// 解构导航切换、登出、锁定方法
const { setNav, logout, lock } = appStore
// 解构打开个人资料方法
const { openSelfProfile } = chatModalsStore
// 解构打开设置方法
const { openSettings } = settingsStore
// 解构 Overlay 打开与关闭全部方法
const { open: openOverlay, closeAll: closeOverlay } = overlayStore

// 获取 Naive UI 消息提示实例
const message = useMessage()

// 更多菜单下拉显隐（受控，登出前先关闭避免残留遮罩）
const menuDropdownShow = ref(false)

/** 侧栏头像：可展示的真实图，否则用本地默认图（避免 avatar 有值但不可加载时透明底+文字看不见） */
const sidebarAvatarUrl = computed(() => {
  const raw = normalizeMediaUrl(userProfile.value.avatar)
  if (raw && isDisplayableMediaUrl(raw)) return raw
  return generateDefaultAvatar(userProfile.value.nickname || t('nav.me'))
})

// 渲染下拉菜单项图标的工厂函数
function renderIcon(icon: Component) {
  return () => h(NIcon, { size: 16 }, { default: () => h(icon) })
}

// 更多菜单下拉选项配置（随语言切换）
const menuOptions = computed<DropdownOption[]>(() => [
  { label: t('nav.history'), key: 'history', icon: renderIcon(TimeOutline) },
  { label: t('nav.update'), key: 'update', icon: renderIcon(RefreshOutline) },
  { label: t('nav.help'), key: 'help', icon: renderIcon(HelpCircleOutline) },
  { type: 'divider', key: 'divider' },
  { label: t('nav.lock'), key: 'lock', icon: renderIcon(LockClosedOutline) },
  {
    label: t('nav.logout'),
    key: 'logout',
    icon: renderIcon(LogOutOutline),
    props: { class: 'lx-menu-danger' }
  }
])

// 主导航项配置：键、图标、标签（随语言切换）
const mainNav = computed(() => [
  { key: 'chat' as NavKey, icon: ChatbubbleEllipsesOutline, label: t('nav.chat') },
  { key: 'contacts' as NavKey, icon: PersonOutline, label: t('nav.contacts') },
  { key: 'favorites' as NavKey, icon: BookmarkOutline, label: t('nav.favorites') },
  { key: 'files' as NavKey, icon: FolderOutline, label: t('nav.files') },
  { key: 'calendar' as NavKey, icon: CalendarOutline, label: t('nav.calendar') },
  { key: 'moments' as NavKey, icon: ApertureOutline, label: t('nav.moments') },
  { key: 'settings' as NavKey, icon: SettingsOutline, label: t('nav.settings') }
])

/** 消息图标未读：普通会话未读 + 日历日程提醒未读（免打扰会话不计入） */
const chatNavUnread = computed(() => {
  const sessionUnread = sessions.value.reduce((sum, s) => {
    if (s.muted) return sum
    return sum + (s.unread || 0)
  }, 0)
  return sessionUnread + (calendarRemindUnreadCount.value || 0)
})

function navBadge(key: NavKey): number {
  if (key === 'chat') return chatNavUnread.value
  if (key === 'contacts') return contactsBadgeCount.value
  return 0
}

// 处理导航项点击
function handleClick(key: NavKey | 'menu') {
  if (key === 'menu') {
    return
  }
  if (key === 'moments') {
    if (window.electronAPI) {
      window.electronAPI.openMoments()
    } else {
      setNav('moments')
    }
    return
  }
  if (key === 'files') {
    handleFilesClick()
    return
  }
  if (key === 'settings') {
    openSettings('account')
    return
  }
  setNav(key)
  refreshNavData(key)
}

// 根据导航类型静默刷新对应数据
function refreshNavData(key: NavKey) {
  switch (key) {
    case 'chat':
      // 刷新聊天会话列表
      void appStore.loadChatSessions()
      break
    case 'contacts':
      // 刷新联系人/好友列表与待处理通知
      void contactsStore.fetchFriends()
      void notificationsStore.fetchFriendRequests()
      void notificationsStore.fetchGroupInvitations()
      break
    case 'favorites':
      // 刷新收藏列表
      void favoritesStore.fetchFavorites()
      break
    case 'files':
      void driveStore.refreshAll()
      break
    case 'calendar':
      // 刷新日历事件
      void calendarStore.fetchEvents()
      break
    case 'moments':
      // 刷新朋友圈/友链
      void momentsStore.fetchMoments()
      break
  }
}

// 文件导航点击处理
function handleFilesClick() {
  setNav('files')
}

// 主题调色盘按钮：关闭 Overlay 与设置后打开外观设置
function handlePaletteClick() {
  closeOverlay()
  settingsStore.closeSettings()
  nextTick(() => {
    openSettings('appearance') // 下一帧打开外观设置页
  })
}

// 执行菜单动作前清理 Overlay 与聊天弹窗
function prepareMenuAction() {
  closeOverlay()
  chatModalsStore.closeAllModals()
}

// n-dropdown 在 @select 后会异步关闭并移除 click-outside 全局监听；
// 若在同一事件循环内同步触发 Modal / 全屏遮罩 / 路由切换 / message，
// 会打断 dropdown 的关闭清理，导致监听器泄漏、持续拦截点击 → UI 卡死。
// 统一用宏任务延迟执行，确保 dropdown 先完成关闭清理。
function runMenuAction(fn: () => void) {
  setTimeout(fn, 100) // 增加延迟，确保 dropdown 动画和清理完全结束
}

// 处理更多菜单选项选中
function handleMenuSelect(key: string | number) {
  switch (key) {
    case 'history':
      runMenuAction(() => {
        prepareMenuAction()
        setNav('chat')
        openOverlay('chat-history') // 打开聊天记录管理 Overlay
      })
      break
    case 'update':
      runMenuAction(() => {
        message.success(t('nav.latestVersion'))
      })
      break
    case 'help':
      runMenuAction(() => {
        prepareMenuAction()
        openOverlay('help') // 打开帮助 Overlay
      })
      break
    case 'lock':
      runMenuAction(() => {
        prepareMenuAction()
        settingsStore.closeSettings()
        lock() // 锁定应用
        message.info(t('nav.locked'))
      })
      break
    case 'settings':
      runMenuAction(() => {
        prepareMenuAction()
        openSettings('account')
      })
      break
    case 'logout':
      menuDropdownShow.value = false
      runMenuAction(() => {
        prepareMenuAction()
        settingsStore.closeSettings()
        void logout()
      })
      break
  }
}

// 点击个人头像打开自己的资料卡
function handleSelfAvatarClick(e: MouseEvent) {
  openSelfProfile(
    {
      nickname: userProfile.value.nickname,
      username: savedLogin.value.username || userProfile.value.username || undefined,
      avatarText: userProfile.value.nickname.charAt(0) || t('nav.me'),
      avatarUrl: userProfile.value.avatar || undefined,
      userId: userProfile.value.userId ? Number(userProfile.value.userId) : undefined
    },
    e
  )
}
</script>

<template>
  <!-- 侧边栏容器 -->
  <div class="sidebar">
    <!-- 个人头像入口 -->
    <button type="button" class="sidebar-avatar" :title="t('nav.profile')" @click="handleSelfAvatarClick">
      <Avatar
        :text="userProfile.nickname.charAt(0) || t('nav.me')"
        color="transparent"
        :size="40"
        :image-url="sidebarAvatarUrl"
      />
    </button>

    <!-- 主导航按钮组 -->
    <div class="nav-top">
      <button
        v-for="item in mainNav"
        :key="item.key"
        type="button"
        class="nav-item"
        :class="{ active: navKey === item.key }"
        :title="item.label"
        :aria-label="item.label"
        @click="handleClick(item.key)"
      >
        <n-icon :component="item.icon" :size="22" />
        <span v-if="navBadge(item.key) > 0" class="nav-badge">
          {{ navBadge(item.key) > 99 ? '99+' : navBadge(item.key) }}
        </span>
      </button>
    </div>

    <!-- 底部工具按钮：主题与更多菜单 -->
    <div class="nav-bottom">
      <button
        type="button"
        class="nav-item"
        :title="t('nav.themePalette')"
        :aria-label="t('nav.themePalette')"
        @click="handlePaletteClick"
      >
        <n-icon :component="ColorPaletteOutline" :size="20" />
      </button>

      <n-dropdown
        v-model:show="menuDropdownShow"
        trigger="click"
        placement="right-end"
        :show-arrow="false"
        :options="menuOptions"
        @select="handleMenuSelect"
      >
        <button type="button" class="nav-item" :title="t('login.menu')" :aria-label="t('login.menu')">
          <n-icon :component="MenuOutline" :size="20" />
        </button>
      </n-dropdown>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  width: var(--lx-sidebar-width);
  height: 100%;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0 10px;
  border-right: none;
  flex-shrink: 0;
}

.sidebar-avatar {
  border: none;
  padding: 0;
  margin: 0 0 12px;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  line-height: 0;
  border-radius: var(--lx-avatar-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.sidebar-avatar:hover {
  transform: scale(1.04);
  opacity: 0.92;
}

.nav-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  align-items: center;
  justify-content: flex-start;
  overflow-y: auto;
}

.nav-bottom {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  align-items: center;
  justify-content: center;
  padding-top: 8px;
}

.nav-item {
  width: 40px;
  height: 40px;
  border: none;
  padding: 0;
  background: transparent;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-nav);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, transform 0.12s ease;
  flex-shrink: 0;
  margin: 0 auto;
  -webkit-app-region: no-drag;
  position: relative;
}

.nav-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--lx-danger, #f04040);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  box-sizing: border-box;
  pointer-events: none;
  box-shadow: 0 0 0 2px var(--lx-bg-sidebar, transparent);
}

.nav-item:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.nav-item.active {
  color: var(--lx-accent);
  background: transparent;
  box-shadow: none;
}

.nav-item.active:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-accent);
}
</style>
