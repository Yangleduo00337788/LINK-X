<script setup lang="ts">
/**
 * 左侧导航栏组件。
 * <p>
 * 提供主导航切换（消息、联系人、收藏等）、个人头像入口、
 * 主题快捷入口与更多菜单（设置、锁定、退出等）。
 * </p>
 */
// Vue 渲染函数、nextTick 与组件类型
import { h, nextTick, ref, type Component } from 'vue'
// Ionicons5 导航与菜单图标
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
// Naive UI 图标、下拉菜单与消息提示
import { NIcon, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
// 头像组件
import Avatar from './Avatar.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../stores/chatModals'
// 设置状态 Store
import { useSettingsStore } from '../stores/settings'
// 全屏 Overlay 状态 Store
import { useOverlayStore } from '../stores/overlay'
// 导航键类型
import type { NavKey } from '../types'

// 获取各 Store 实例
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const settingsStore = useSettingsStore()
const overlayStore = useOverlayStore()
// 解构导航键、用户资料、已保存登录信息
const { navKey, userProfile, savedLogin } = storeToRefs(appStore)
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

// 渲染下拉菜单项图标的工厂函数
function renderIcon(icon: Component) {
  return () => h(NIcon, { size: 16 }, { default: () => h(icon) })
}

// 更多菜单下拉选项配置
const menuOptions: DropdownOption[] = [
  { label: '聊天记录管理', key: 'history', icon: renderIcon(TimeOutline) },
  { label: '检查更新', key: 'update', icon: renderIcon(RefreshOutline) },
  { label: '帮助', key: 'help', icon: renderIcon(HelpCircleOutline) },
  { type: 'divider', key: 'divider' }, // 分隔线
  { label: '锁定', key: 'lock', icon: renderIcon(LockClosedOutline) },
  { label: '设置', key: 'settings', icon: renderIcon(SettingsOutline) },
  {
    label: '退出账号',
    key: 'logout',
    icon: renderIcon(LogOutOutline),
    props: { class: 'lx-menu-danger' } // 危险操作样式
  }
]

// 主导航项配置：键、图标、标签
const mainNav: { key: NavKey; icon: typeof ChatbubbleEllipsesOutline; label: string }[] = [
  { key: 'chat', icon: ChatbubbleEllipsesOutline, label: '消息' },
  { key: 'contacts', icon: PersonOutline, label: '联系人' },
  { key: 'favorites', icon: BookmarkOutline, label: '收藏' },
  { key: 'files', icon: FolderOutline, label: '文件' },
  { key: 'calendar', icon: CalendarOutline, label: '日历' },
  { key: 'apps', icon: GridOutline, label: '应用' },
  { key: 'moments', icon: ApertureOutline, label: '友链' }
]

// 处理导航项点击
function handleClick(key: NavKey | 'menu') {
  if (key === 'menu') {
    return // 菜单按钮由 dropdown 自身处理
  }
  if (key === 'moments') {
    // 友链：Electron 下打开独立窗口，浏览器内切换导航
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
  setNav(key) // 其他导航直接切换
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
        message.success('当前已是最新版本：LinkX v1.0.0')
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
        message.info('LinkX 已锁定')
      })
      break
    case 'settings':
      runMenuAction(() => {
        prepareMenuAction()
        openSettings() // 打开设置弹窗
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
      avatarText: userProfile.value.nickname.charAt(0) || '我',
      avatarUrl: userProfile.value.avatar || undefined,
      userId: userProfile.value.userId || undefined
    },
    e
  )
}
</script>

<template>
  <!-- 侧边栏容器 -->
  <div class="sidebar">
    <!-- 个人头像入口 -->
    <button type="button" class="sidebar-avatar" title="个人资料" @click="handleSelfAvatarClick">
      <Avatar
        :text="userProfile.nickname.charAt(0) || '我'"
        :color="userProfile.avatar ? 'transparent' : 'var(--lx-success)'"
        :size="40"
        :image-url="userProfile.avatar || undefined"
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
      </button>
    </div>

    <!-- 底部工具按钮：主题与更多菜单 -->
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
        v-model:show="menuDropdownShow"
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
