<script setup lang="ts">
import { h, nextTick, type Component } from 'vue'
import {
  ChatbubbleEllipsesOutline,
  PersonOutline,
  ApertureOutline,
  MenuOutline,
  GridOutline,
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
import { NIcon, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import Avatar from './Avatar.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { useSettingsStore } from '../stores/settings'
import { useOverlayStore } from '../stores/overlay'
import type { NavKey } from '../types'

const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const settingsStore = useSettingsStore()
const overlayStore = useOverlayStore()
const { navKey, userProfile, savedLogin } = storeToRefs(appStore)
const { setNav, logout, lock } = appStore
const { openSelfProfile } = chatModalsStore
const { openSettings } = settingsStore
const { open: openOverlay, closeAll: closeOverlay } = overlayStore

const message = useMessage()

function renderIcon(icon: Component) {
  return () => h(NIcon, { size: 16 }, { default: () => h(icon) })
}

const menuOptions: DropdownOption[] = [
  { label: '聊天记录管理', key: 'history', icon: renderIcon(TimeOutline) },
  { label: '检查更新', key: 'update', icon: renderIcon(RefreshOutline) },
  { label: '帮助', key: 'help', icon: renderIcon(HelpCircleOutline) },
  { type: 'divider', key: 'divider' },
  { label: '锁定', key: 'lock', icon: renderIcon(LockClosedOutline) },
  { label: '设置', key: 'settings', icon: renderIcon(SettingsOutline) },
  {
    label: '退出账号',
    key: 'logout',
    icon: renderIcon(LogOutOutline),
    props: { class: 'lx-menu-danger' }
  }
]

const mainNav: { key: NavKey; icon: typeof ChatbubbleEllipsesOutline; label: string }[] = [
  { key: 'chat', icon: ChatbubbleEllipsesOutline, label: '消息' },
  { key: 'contacts', icon: PersonOutline, label: '联系人' },
  { key: 'favorites', icon: BookmarkOutline, label: '收藏' },
  { key: 'files', icon: FolderOutline, label: '文件' },
  { key: 'calendar', icon: CalendarOutline, label: '日历' },
  { key: 'apps', icon: GridOutline, label: '应用' },
  { key: 'moments', icon: ApertureOutline, label: '友链' }
]

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
  setNav(key)
}

function handleFilesClick() {
  setNav('files')
}

function handlePaletteClick() {
  closeOverlay()
  settingsStore.closeSettings()
  nextTick(() => {
    openSettings('appearance')
  })
}

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

function handleMenuSelect(key: string | number) {
  switch (key) {
    case 'history':
      runMenuAction(() => {
        prepareMenuAction()
        setNav('chat')
        openOverlay('chat-history')
      })
      break
    case 'update':
      runMenuAction(() => {
        message.success('当前已是最新版本：LinkX v1.0.0')
      })
      break
    case 'help':
      runMenuAction(() => {
        prepareMenuAction()
        openOverlay('help')
      })
      break
    case 'lock':
      runMenuAction(() => {
        prepareMenuAction()
        settingsStore.closeSettings()
        lock()
        message.info('LinkX 已锁定')
      })
      break
    case 'settings':
      runMenuAction(() => {
        prepareMenuAction()
        openSettings()
      })
      break
    case 'logout':
      runMenuAction(() => {
        prepareMenuAction()
        settingsStore.closeSettings()
        logout()
      })
      break
  }
}

function handleSelfAvatarClick(e: MouseEvent) {
  openSelfProfile(
    {
      nickname: userProfile.value.nickname,
      username: savedLogin.value.username || undefined,
      avatarText: userProfile.value.nickname.charAt(0) || '我'
    },
    e
  )
}
</script>

<template>
  <div class="sidebar">
    <button type="button" class="sidebar-avatar" title="个人资料" @click="handleSelfAvatarClick">
      <Avatar
        :text="userProfile.nickname.charAt(0) || '我'"
        color="var(--lx-success)"
        :size="40"
        image-url="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
      />
    </button>

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
      </button>
    </div>

    <div class="nav-bottom">
      <button
        type="button"
        class="nav-item"
        title="主题调色盘"
        aria-label="主题调色盘"
        @click="handlePaletteClick"
      >
        <n-icon :component="ColorPaletteOutline" :size="20" />
      </button>

      <n-dropdown
        trigger="click"
        placement="right-end"
        :show-arrow="false"
        :options="menuOptions"
        @select="handleMenuSelect"
      >
        <button type="button" class="nav-item" title="菜单" aria-label="菜单">
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
