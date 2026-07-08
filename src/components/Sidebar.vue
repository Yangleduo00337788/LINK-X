<script setup lang="ts">
import {
  ChatbubbleEllipsesOutline,
  PersonOutline,
  ApertureOutline,
  MenuOutline,
  GridOutline,
  BookmarkOutline,
  FolderOutline,
  ColorPaletteOutline,
  TimeOutline,
  RefreshOutline,
  HelpCircleOutline,
  LockClosedOutline,
  SettingsOutline,
  LogOutOutline,
  ChevronForwardOutline
} from '@vicons/ionicons5'
import { NIcon, NPopover, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { useSettingsStore } from '../stores/settings'
import { useOverlayStore } from '../stores/overlay'
import type { NavKey } from '../types'

const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const settingsStore = useSettingsStore()
const { navKey } = storeToRefs(appStore)
const { setNav, logout, lock } = appStore
const { openMomentsModal } = chatModalsStore
const { openSettings } = settingsStore

const overlayStore = useOverlayStore()
const { open: openOverlay } = overlayStore

const mainNav: { key: NavKey; icon: typeof ChatbubbleEllipsesOutline; label: string }[] = [
  { key: 'chat', icon: ChatbubbleEllipsesOutline, label: '消息' },
  { key: 'contacts', icon: PersonOutline, label: '联系人' },
  { key: 'favorites', icon: BookmarkOutline, label: '收藏' },
  { key: 'files', icon: FolderOutline, label: '文件' },
  { key: 'apps', icon: GridOutline, label: '应用' },
  { key: 'moments', icon: ApertureOutline, label: '友链' }
]

function handleClick(key: NavKey | 'menu') {
  if (key === 'menu') {
    return
  }
  if (key === 'moments') {
    // 调用 Electron API 打开独立的友链窗口
    if (window.electronAPI) {
      window.electronAPI.openMoments()
    } else {
      // 网页版回退逻辑（如果需要的话，也可以保持原本的 Modal 方式）
      openMomentsModal()
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
  openSettings('appearance')
}

const message = useMessage()

function handleHistoryClick() {
  setNav('chat')
  openOverlay('chat-history')
}

function handleUpdateClick() {
  message.success('当前已是最新版本：LinkX v1.0.0')
}

function handleHelpClick() {
  openOverlay('help')
}

function handleLockClick() {
  lock()
}

function handleSettingsClick() {
  openSettings()
}

function handleLogoutClick() {
  message.success('已安全退出账号')
  logout()
}
</script>

<template>
  <div class="sidebar">
    <div class="nav-top">
      <div
        v-for="item in mainNav"
        :key="item.key"
        class="nav-item"
        :class="{ active: navKey === item.key }"
        :title="item.label"
        @click="handleClick(item.key)"
      >
        <n-icon :component="item.icon" :size="22" />
      </div>

    </div>

    <div class="nav-bottom">
      <div
        class="nav-item"
        title="主题调色盘"
        @click="handlePaletteClick"
      >
        <n-icon :component="ColorPaletteOutline" :size="20" />
      </div>

      <n-popover placement="right-end" trigger="click" :show-arrow="false" style="padding: 0; border-radius: var(--lx-radius); overflow: hidden; box-shadow: 0 4px 24px var(--lx-shadow-color);">
        <template #trigger>
          <div
            class="nav-item"
            title="菜单"
          >
            <n-icon :component="MenuOutline" :size="20" />
          </div>
        </template>
        <div class="more-menu">
          <div class="menu-list">
            <div class="menu-list-item" @click="handleHistoryClick">
              <n-icon :component="TimeOutline" :size="16" />
              <span>聊天记录管理</span>
            </div>
            <div class="menu-list-item" @click="handleUpdateClick">
              <n-icon :component="RefreshOutline" :size="16" />
              <span>检查更新</span>
            </div>
            <div class="menu-list-item" @click="handleHelpClick">
              <n-icon :component="HelpCircleOutline" :size="16" />
              <span>帮助</span>
              <n-icon :component="ChevronForwardOutline" class="arrow" />
            </div>
            <div class="menu-list-item" @click="handleLockClick">
              <n-icon :component="LockClosedOutline" :size="16" />
              <span>锁定</span>
            </div>
            <div class="menu-list-item" @click="handleSettingsClick">
              <n-icon :component="SettingsOutline" :size="16" />
              <span>设置</span>
            </div>
            <div class="menu-list-item danger" @click="handleLogoutClick">
              <n-icon :component="LogOutOutline" :size="16" />
              <span>退出账号</span>
            </div>
          </div>
        </div>
      </n-popover>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  width: 52px;
  height: 100%;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0 8px;
  border-right: none;
  flex-shrink: 0;
}

.nav-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  align-items: center;
  overflow-y: auto;
}

.nav-divider {
  width: 22px;
  height: 1px;
  background: var(--lx-bg-active);
  margin: 6px 0;
}

.nav-bottom {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  align-items: center;
  padding-top: 6px;
}

.nav-item {
  width: 40px;
  height: 40px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-nav);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, transform 0.12s ease;
  flex-shrink: 0;
}

.nav-item--subtle {
  color: var(--lx-text-nav-subtle);
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



.more-menu {
  width: 220px;
  background: var(--lx-bg-card);
  display: flex;
  flex-direction: column;
  border-radius: var(--lx-radius);
}

.menu-list {
  padding: 8px;
  display: flex;
  flex-direction: column;
}

.menu-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  color: var(--lx-text-body);
  font-size: 14px;
  transition: background 0.15s;
}

.menu-list-item:hover {
  background: var(--lx-bg-hover);
}

.menu-list-item .arrow {
  margin-left: auto;
  color: var(--lx-text-muted);
}

.menu-list-item.danger:hover {
  color: var(--lx-danger);
}
</style>