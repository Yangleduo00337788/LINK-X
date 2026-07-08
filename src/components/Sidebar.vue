<script setup lang="ts">
import {
  ChatbubbleEllipsesOutline,
  PersonOutline,
  ApertureOutline,
  MenuOutline,
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
import { useSecondaryViewStore } from '../stores/secondaryView'
import { useChatModalsStore } from '../stores/chatModals'
import { useSettingsStore } from '../stores/settings'
import type { NavKey } from '../types'

const appStore = useAppStore()
const secondaryViewStore = useSecondaryViewStore()
const chatModalsStore = useChatModalsStore()
const settingsStore = useSettingsStore()
const { navKey } = storeToRefs(appStore)
const { menuOpen } = storeToRefs(secondaryViewStore)
const { setNav, logout, lock } = appStore
const { openMomentsModal } = chatModalsStore
const { openSettings } = settingsStore

const message = useMessage()

const mainNav: { key: NavKey; icon: typeof ChatbubbleEllipsesOutline }[] = [
  { key: 'chat', icon: ChatbubbleEllipsesOutline },
  { key: 'contacts', icon: PersonOutline },
  { key: 'favorites', icon: BookmarkOutline },
  { key: 'files', icon: FolderOutline },
  { key: 'moments', icon: ApertureOutline }
]

function handleClick(key: NavKey | 'menu') {
  if (key === 'menu') {
    return
  }
  if (key === 'moments') {
    // 调用 Electron API 打开独立的 X友圈 窗口
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

function handleHistoryClick() {
  message.info('聊天记录管理功能开发中')
}

function handleUpdateClick() {
  message.success('当前已是最新版本：LinkX v1.0.0')
}

function handleHelpClick() {
  message.info('帮助与反馈中心')
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
        :class="{ active: navKey === item.key && !menuOpen }"
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

      <n-popover placement="right-end" trigger="click" :show-arrow="false" style="padding: 0; border-radius: var(--lx-radius); overflow: hidden; box-shadow: 0 4px 24px rgba(0,0,0,0.12);">
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
  background: rgba(0, 0, 0, 0.08);
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
  color: #5c5c5c;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, transform 0.12s ease;
  flex-shrink: 0;
}

.nav-item--subtle {
  color: #7a7a7a;
}

.nav-item:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #333;
}

.nav-item.active {
  color: #12b7f5;
  background: transparent;
  box-shadow: none;
}

.nav-item.active:hover {
  background: rgba(0, 0, 0, 0.04);
  color: #12b7f5;
}



.more-menu {
  width: 220px;
  background: #ffffff;
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
  color: #333;
  font-size: 14px;
  transition: background 0.15s;
}

.menu-list-item:hover {
  background: #f0f0f0;
}

.menu-list-item .arrow {
  margin-left: auto;
  color: #999;
}

.menu-list-item.danger:hover {
  color: #fa5151;
}
</style>