<script setup lang="ts">
/**
 * 联系人左侧面板组件。
 * <p>
 * 展示好友与群聊列表，支持搜索、Tab 切换、通知入口，
 * 单击打开资料卡、双击发起聊天。
 * </p>
 */
// Vue 响应式与计算属性
import { ref, computed } from 'vue'
// Naive UI 图标、骨架屏、虚拟列表与消息提示
import { NIcon, NSkeleton, NVirtualList, useMessage } from 'naive-ui'
// Ionicons5 右箭头图标
import { ChevronForwardOutline } from '@vicons/ionicons5'
// 面板搜索栏
import PanelSearchBar from './PanelSearchBar.vue'
// 头像组件
import Avatar from './Avatar.vue'
// 空状态组件
import EmptyState from './common/EmptyState.vue'
// 联系人 Store
import { useContactsStore } from '../stores/contacts'
// 好友通知 Store
import { useNotificationsStore } from '../stores/notifications'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../stores/chatModals'
// 联系人与会话类型
import type { ContactItem } from '../types'

// 获取联系人 Store 实例
const contactsStore = useContactsStore()
const message = useMessage()
const notificationsStore = useNotificationsStore()
// 解构联系人列表
const { items: contacts } = storeToRefs(contactsStore)
const { pendingFriendCount } = storeToRefs(notificationsStore)
// 获取应用 Store 实例
const appStore = useAppStore()
// 获取聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 解构联系人当前视图、当前会话 ID、加载状态、会话列表
const { contactsActiveView, currentSessionId, isLoading, sessions } = storeToRefs(appStore)
// 解构发起聊天、选中会话、重置联系人视图方法
const { startChatWithContact, selectSession, resetContactsView } = appStore
// 解构发起群聊、综合搜索、打开联系人资料方法
const { openCreateGroup, openComprehensiveSearch, openContactProfile } = chatModalsStore
// 搜索关键词
const search = ref('')
// 当前 Tab：好友或群聊
const activeTab = ref<'friends' | 'groups'>('friends')

// 添加按钮下拉选项
const addOptions = [
  { label: '发起群聊', key: 'group' },
  { label: '添加好友/群聊', key: 'friend' }
]

// 处理添加按钮下拉选项选中
function onAddSelect(key: string) {
  if (key === 'group') {
    openCreateGroup()
    return
  }
  if (key === 'friend') {
    openComprehensiveSearch()
  }
}

// 根据搜索词过滤联系人
const filteredContacts = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return contacts.value
  return contacts.value.filter(c => c.name.toLowerCase().includes(q))
})

// 计算好友分组及在线/总数统计
const friendGroups = computed(() => {
  const friends = filteredContacts.value.filter(c => c.group === '我的好友')
  const online = friends.filter(c => c.online).length // 在线好友数
  return [
    { name: '我的好友', online, total: friends.length, items: friends }
  ]
})

// 计算群聊分组：置顶群聊与我加入的群聊
const chatGroups = computed(() => {
  const q = search.value.trim().toLowerCase()
  let list = sessions.value.filter(s => s.isGroup) // 仅群聊会话
  if (q) list = list.filter(s => s.name.toLowerCase().includes(q)) // 搜索过滤
  const pinned = list.filter(s => s.pinned) // 置顶群
  const joined = list.filter(s => !s.pinned) // 非置顶群
  return [
    { name: '置顶群聊', total: pinned.length, items: pinned },
    { name: '我加入的群聊', total: joined.length, items: joined }
  ].filter(g => g.total > 0 || !q) // 无搜索时保留空分组，有搜索时隐藏空分组
})

// 根据联系人查找对应单聊会话 ID
function contactSessionId(c: ContactItem) {
  const s = sessions.value.find(x => !x.isGroup && (x.id === c.id || x.name === c.name))
  return s?.id ?? c.id // 找不到则使用联系人 ID
}

// 点击群聊项：重置联系人视图并选中该群会话
function openGroupSession(session: import('../types').ChatSession) {
  resetContactsView()
  selectSession(session)
}

// 单击联系人：打开资料卡
function handleContactClick(c: ContactItem, e: MouseEvent) {
  resetContactsView()
  openContactProfile(c, e)
}

// 双击联系人：直接发起聊天
async function handleContactDblClick(c: ContactItem) {
  resetContactsView()
  try {
    await startChatWithContact(c)
  } catch (error) {
    message.error((error as Error).message || '打开会话失败')
  }
}

// 切换到好友/群通知视图
function setView(view: 'friend-notifs' | 'group-notifs') {
  currentSessionId.value = null // 清除当前会话选中
  contactsActiveView.value = view // 设置联系人右侧视图
}

// Tab 切换时重置联系人右侧视图
function onTabChange(tab: 'friends' | 'groups') {
  activeTab.value = tab
  resetContactsView()
}
</script>

<template>
  <!-- 联系人面板容器 -->
  <div class="contacts-panel">
    <!-- 顶部搜索栏 -->
    <PanelSearchBar
      v-model="search"
      placeholder="搜索"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />

    <!-- 好友通知与群通知入口 -->
    <div class="top-actions">
      <div class="action-list">
        <div class="action-item" :class="{ active: contactsActiveView === 'friend-notifs' }" @click="setView('friend-notifs')">
          <span>好友通知</span>
          <span v-if="pendingFriendCount" class="notif-badge">{{ pendingFriendCount }}</span>
          <n-icon :component="ChevronForwardOutline" />
        </div>
        <div class="action-item" :class="{ active: contactsActiveView === 'group-notifs' }" @click="setView('group-notifs')">
          <span>群通知</span>
          <n-icon :component="ChevronForwardOutline" />
        </div>
      </div>
    </div>

    <!-- 好友/群聊 Tab 切换 -->
    <div class="tabs" :class="activeTab">
      <div class="tab-slider"></div>
      <div class="tab-item" :class="{ active: activeTab === 'friends' }" @click="onTabChange('friends')">好友</div>
      <div class="tab-item" :class="{ active: activeTab === 'groups' }" @click="onTabChange('groups')">群聊</div>
    </div>

    <!-- 列表容器 -->
    <div class="list-container">
      <!-- 加载中骨架屏 -->
      <template v-if="isLoading">
        <div class="skeleton-item" v-for="i in 8" :key="i">
          <n-skeleton size="large" class="skeleton-avatar" />
          <div class="skeleton-info">
            <n-skeleton text width="50%" height="16px" class="skeleton-title" />
            <n-skeleton text width="30%" height="12px" class="skeleton-desc" />
          </div>
        </div>
      </template>

      <!-- 无搜索结果 -->
      <template v-else-if="filteredContacts.length === 0 && search">
        <EmptyState title="无匹配联系人" description="换个关键词试试" />
      </template>

      <!-- 好友或群聊列表 -->
      <template v-else>
        <!-- 好友 Tab：虚拟滚动列表 -->
        <div v-if="activeTab === 'friends'" class="contacts-list" style="height: 100%; display: flex; flex-direction: column;">
          <div class="group-header" style="flex-shrink: 0;">
            <span class="group-name">我的好友</span>
            <span class="group-count">{{ friendGroups[0].online }}/{{ friendGroups[0].total }}</span>
          </div>
          <n-virtual-list
            style="flex: 1; height: 100%; min-height: 0;"
            :item-size="76"
            :items="friendGroups[0].items"
            item-key="id"
          >
            <template #default="{ item }">
              <div
                class="contact-row"
                :class="{ active: currentSessionId === contactSessionId(item) }"
                @click="handleContactClick(item, $event)"
                @dblclick="handleContactDblClick(item)"
              >
                <Avatar
                  :text="item.avatarText"
                  :color="item.avatarColor"
                  :size="46"
                  :image-url="item.avatarUrl"
                />
                <div class="info">
                  <div class="name-row">
                    <span class="name">{{ item.name }}</span>
                    <span class="status-dot" :class="{ online: item.online }"></span>
                  </div>
                  <span class="status">{{ item.online ? '在线' : '离线' }}</span>
                </div>
              </div>
            </template>
          </n-virtual-list>
        </div>

        <!-- 群聊 Tab：分组列表 -->
        <div v-if="activeTab === 'groups'" class="groups-list">
          <template v-if="chatGroups.length === 0">
            <EmptyState title="暂无群聊" description="发起群聊或加入群聊后将显示在这里" />
          </template>
          <template v-else>
            <div v-for="group in chatGroups" :key="group.name" class="group-section">
              <div class="group-header">
                <span class="group-name">{{ group.name }}</span>
                <span class="group-count">{{ group.total }}</span>
              </div>
              <div
                v-for="item in group.items"
                :key="item.id"
                class="contact-row"
                :class="{ active: currentSessionId === item.id }"
                @click="openGroupSession(item)"
              >
                <Avatar
                  :text="item.avatarText"
                  :color="item.avatarColor"
                  :size="46"
                  :image-url="item.avatarUrl"
                />
                <div class="info">
                  <span class="name">{{ item.name }}</span>
                  <span class="status">{{ item.lastMessage }}</span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.contacts-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.search-wrap {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  gap: 8px;
}

.add-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-input);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--lx-text-secondary);
}

.add-btn:hover {
  background: var(--lx-bg-panel-deep);
}

.top-actions {
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  font-size: 14px;
  color: var(--lx-text-body);
  background: var(--lx-bg-card);
}

.action-item:hover {
  background: var(--lx-bg-panel);
}

.action-item.active {
  background: rgba(18, 183, 245, 0.08);
  color: var(--lx-accent);
}

.notif-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: auto;
  margin-right: 6px;
  border-radius: 9px;
  background: var(--lx-accent);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.tabs {
  display: flex;
  position: relative;
  margin: 16px 12px 8px;
  background: var(--lx-bg-input);
  border-radius: var(--lx-radius);
  padding: 3px;
}

.tab-slider {
  position: absolute;
  top: 3px;
  left: 3px;
  width: calc(50% - 3px);
  height: calc(100% - 6px);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 2px 4px var(--lx-border-light);
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  z-index: 1;
}

.tabs.groups .tab-slider {
  transform: translateX(100%);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  border-radius: var(--lx-radius);
  position: relative;
  z-index: 2;
  transition: color 0.3s ease;
}

.tab-item.active {
  color: var(--lx-text);
  font-weight: 500;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.contacts-list,
.groups-list {
  padding: 0 12px;
}

.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 8px 8px;
  font-size: 13px;
  color: var(--lx-text-muted);
  font-weight: 500;
}

.group-name {
  font-size: 12px;
}

.group-count {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.contact-row {
  height: 72px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  margin-bottom: 4px;
  gap: 12px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  transition: background 0.2s ease;
  background: var(--lx-bg-card);
}

.contact-row:hover {
  background: var(--lx-bg-panel);
}

.contact-row.active {
  background: rgba(18, 183, 245, 0.08);
}

.info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  font-size: 14px;
  color: var(--lx-text);
  font-weight: 500;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lx-border-strong);
  flex-shrink: 0;
}

.status-dot.online {
  background: var(--lx-success);
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.2);
}

.status {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.skeleton-item {
  display: flex;
  padding: 12px 16px;
  gap: 12px;
  align-items: center;
}

.skeleton-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
