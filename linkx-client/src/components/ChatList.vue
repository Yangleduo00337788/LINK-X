<script setup lang="ts">
/**
 * 聊天会话列表组件。
 * <p>
 * 展示会话列表，支持搜索过滤、虚拟滚动、右键菜单（置顶/免打扰/删除），
 * 以及通过添加按钮发起群聊或添加好友。
 * </p>
 */
// Vue 响应式与计算属性
import { ref, computed } from 'vue'
// Naive UI 图标、骨架屏、下拉菜单、虚拟列表与消息提示
import { NIcon, NSkeleton, NDropdown, NVirtualList, useMessage, type DropdownOption } from 'naive-ui'
// Ionicons5 手机、免打扰、警告图标
import { PhonePortraitOutline, NotificationsOffOutline, WarningOutline } from '@vicons/ionicons5'
// 置顶图标组件
import PinIcon from './icons/PinIcon.vue'
// 面板搜索栏
import PanelSearchBar from './PanelSearchBar.vue'
// 头像组件
import Avatar from './Avatar.vue'
// 空状态组件
import EmptyState from './common/EmptyState.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../stores/chatModals'
// 会话类型定义
import type { ChatSession } from '../types'

// 获取 Naive UI 消息提示实例
const message = useMessage()
// 获取应用 Store 实例
const appStore = useAppStore()
// 获取聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 解构排序后的会话、当前会话 ID、加载与离线状态
const { sortedSessions, currentSessionId, isLoading, isOffline } = storeToRefs(appStore)
// 解构会话选择、置顶、免打扰、删除方法
const { selectSession, toggleSessionPin, toggleSessionMute, deleteSession } = appStore
// 解构发起群聊与综合搜索方法
const { openCreateGroup, openComprehensiveSearch } = chatModalsStore
// 搜索关键词
const searchValue = ref('')

// 右键菜单相关状态
const contextSession = ref<ChatSession | null>(null) // 右键选中的会话
const contextMenuShow = ref(false) // 是否显示右键菜单
const contextMenuX = ref(0) // 菜单 X 坐标
const contextMenuY = ref(0) // 菜单 Y 坐标

// 根据搜索关键词过滤会话列表
const filteredSessions = computed(() => {
  const q = searchValue.value.trim().toLowerCase() // 规范化搜索词
  if (!q) return sortedSessions.value // 无搜索词返回全部
  return sortedSessions.value.filter(
    s => s.name.toLowerCase().includes(q) || s.lastMessage.toLowerCase().includes(q) // 匹配名称或最后消息
  )
})

// 根据当前右键会话动态生成菜单选项
const contextMenuOptions = computed<DropdownOption[]>(() => {
  const s = contextSession.value
  if (!s) return [] // 无选中会话则空菜单
  return [
    { label: s.pinned ? '取消置顶' : '置顶', key: 'pin' },
    { label: s.muted ? '取消免打扰' : '免打扰', key: 'mute' },
    { type: 'divider', key: 'd1' },
    { label: '删除会话', key: 'delete' }
  ]
})

// 添加按钮下拉选项
const addOptions = [
  { label: '发起群聊', key: 'group' },
  { label: '添加好友/群聊', key: 'friend' }
]

// 点击会话项选中该会话
function onSelect(session: ChatSession) {
  selectSession(session)
}

// 处理添加按钮下拉选项选中
function onAddSelect(key: string) {
  if (key === 'group') {
    openCreateGroup() // 打开发起群聊弹窗
    return
  }
  if (key === 'friend') {
    openComprehensiveSearch()
  }
}

// 会话项右键菜单：记录坐标与目标会话
function onSessionContext(e: MouseEvent, session: ChatSession) {
  e.preventDefault() // 阻止浏览器默认右键菜单
  contextSession.value = session
  contextMenuX.value = e.clientX
  contextMenuY.value = e.clientY
  contextMenuShow.value = true
}

// 处理右键菜单选项选中
function onContextMenuSelect(key: string) {
  const s = contextSession.value
  if (!s) return
  if (key === 'pin') {
    const wasPinned = s.pinned // 记录操作前状态用于提示文案
    toggleSessionPin(s.id)
    message.success(wasPinned ? '已取消置顶' : '已置顶')
  } else if (key === 'mute') {
    const wasMuted = s.muted
    toggleSessionMute(s.id)
    message.success(wasMuted ? '已取消免打扰' : '已开启免打扰')
  } else if (key === 'delete') {
    deleteSession(s.id)
    message.success('已删除会话')
  }
  contextMenuShow.value = false // 关闭菜单
}
</script>

<template>
  <!-- 聊天列表容器 -->
  <div class="chat-list">
    <!-- 顶部搜索栏与添加按钮 -->
    <PanelSearchBar
      v-model="searchValue"
      placeholder="搜索"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />

    <!-- 离线提示横幅 -->
    <div v-if="isOffline" class="offline-banner">
      <n-icon :component="WarningOutline" :size="16" />
      <span>网络连接已断开，请检查网络设置</span>
    </div>

    <!-- 会话列表区域 -->
    <div class="session-list">
      <!-- 加载中骨架屏 -->
      <template v-if="isLoading">
        <div class="skeleton-item" v-for="i in 8" :key="i">
          <n-skeleton size="large" class="skeleton-avatar" />
          <div class="skeleton-info">
            <n-skeleton text width="60%" height="16px" class="skeleton-title" />
            <n-skeleton text width="80%" height="14px" class="skeleton-desc" />
          </div>
        </div>
      </template>

      <!-- 无匹配结果空状态 -->
      <template v-else-if="filteredSessions.length === 0">
        <EmptyState title="无匹配的会话" description="请尝试其他关键词" />
      </template>

      <!-- 虚拟滚动会话列表 -->
      <template v-else>
        <n-virtual-list
          style="max-height: 100%; height: 100%"
          :item-size="68"
          :items="filteredSessions"
          item-key="id"
        >
          <template #default="{ item: session }">
            <div
              class="session-item"
              :class="{ active: currentSessionId === session.id, pinned: session.pinned }"
              @click="onSelect(session)"
              @contextmenu="onSessionContext($event, session)"
            >
              <!-- 头像与未读角标 -->
              <div class="avatar-wrapper">
                <Avatar
                  :text="session.avatarText"
                  :color="session.avatarColor"
                  :size="44"
                  :icon="session.name === '我的手机' ? PhonePortraitOutline : undefined"
                />
                <div v-if="session.unread && !session.muted" class="unread-badge">
                  {{ session.unread > 99 ? '99+' : session.unread }}
                </div>
              </div>

              <!-- 会话名称、时间与最后消息 -->
              <div class="session-content">
                <div class="session-top">
                  <span class="session-name">
                    <PinIcon v-if="session.pinned" :size="12" class="pin-icon" />
                    {{ session.name }}
                  </span>
                  <span class="session-meta">
                    <n-icon
                      v-if="session.muted"
                      :component="NotificationsOffOutline"
                      :size="14"
                      class="mute-icon"
                    />
                    <span class="session-time">{{ session.time }}</span>
                  </span>
                </div>
                <div class="session-bottom">
                  <span class="last-message">{{ session.lastMessage }}</span>
                </div>
              </div>
            </div>
          </template>
        </n-virtual-list>
      </template>
    </div>

    <!-- 会话右键菜单（手动触发定位） -->
    <n-dropdown
      trigger="manual"
      placement="bottom-start"
      :show="contextMenuShow"
      :x="contextMenuX"
      :y="contextMenuY"
      :options="contextMenuOptions"
      @select="onContextMenuSelect"
      @clickoutside="contextMenuShow = false"
    />
  </div>
</template>

<style scoped>
.chat-list {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.offline-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: var(--lx-danger-bg-soft);
  color: var(--lx-danger);
  padding: 8px;
  font-size: 12px;
  border-bottom: 1px solid #ffccc7;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  background: var(--lx-bg-panel);
  padding: 4px 0;
}

.session-item {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 10px 0 12px;
  margin: 0 6px;
  gap: 12px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  transition: background 0.16s ease;
}

.session-item.pinned {
  background: rgba(18, 183, 245, 0.06);
}

.session-item:hover {
  background: var(--lx-bg-hover);
}

.session-item.active {
  background: rgba(18, 183, 245, 0.14);
}

.avatar-wrapper {
  position: relative;
  flex-shrink: 0;
}

.unread-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--lx-radius);
  background: linear-gradient(180deg, #ff6b6b 0%, #f04040 100%);
  color: var(--lx-bg-card);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--lx-bg-panel);
  box-shadow: 0 1px 3px rgba(240, 64, 64, 0.35);
}

.session-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.session-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-name {
  font-size: 14px;
  color: var(--lx-text-body);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 4px;
}

.pin-icon {
  color: var(--lx-accent);
  flex-shrink: 0;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.mute-icon {
  color: #b0b0b0;
}

.session-time {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.last-message {
  font-size: 12px;
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skeleton-item {
  display: flex;
  padding: 12px 14px;
  gap: 10px;
  align-items: center;
}

.skeleton-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
