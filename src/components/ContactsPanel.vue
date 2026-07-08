<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon, NSkeleton } from 'naive-ui'
import { ChevronForwardOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import EmptyState from './common/EmptyState.vue'
import { contacts } from '../data/mockData'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import type { ContactItem } from '../types'

const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const { contactsActiveView, currentSessionId, isLoading } = storeToRefs(appStore)
const { openCreateGroup, openComprehensiveSearch, openContactProfile } = chatModalsStore
const search = ref('')
const activeTab = ref<'friends' | 'groups'>('friends')

const addOptions = [
  { label: '发起群聊', key: 'group' },
  { label: '添加好友', key: 'friend' }
]

function onAddSelect(key: string) {
  if (key === 'group') {
    openCreateGroup()
    return
  }
  if (key === 'friend') {
    openComprehensiveSearch()
  }
}

// Define friend groups and group chat categories with counts
const filteredContacts = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return contacts
  return contacts.filter(c => c.name.toLowerCase().includes(q))
})

const friendGroups = computed(() => {
  return [
    { name: '我的好友', online: 6, total: 10, items: filteredContacts.value.filter(c => c.group === '我的好友') },
  ]
})

const chatGroups = [
  { name: '置顶群聊', total: 0, items: [] },
  { name: '未命名的群聊', total: 1, items: [] },
  { name: '我创建的群聊', total: 1, items: [] },
  { name: '我管理的群聊', total: 0, items: [] },
  { name: '我加入的群聊', total: 19, items: [] },
]

function handleContactClick(c: ContactItem) {
  // 先打开联系人资料弹窗
  openContactProfile(c)
}

function setView(view: 'friend-notifs' | 'group-notifs') {
  currentSessionId.value = null
  contactsActiveView.value = view
}
</script>

<template>
  <div class="contacts-panel">
    <PanelSearchBar
      v-model="search"
      placeholder="搜索"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />

    <div class="top-actions">
      <div class="action-list">
        <div class="action-item" :class="{ active: contactsActiveView === 'friend-notifs' }" @click="setView('friend-notifs')">
          <span>好友通知</span>
          <n-icon :component="ChevronForwardOutline" />
        </div>
        <div class="action-item" :class="{ active: contactsActiveView === 'group-notifs' }" @click="setView('group-notifs')">
          <span>群通知</span>
          <n-icon :component="ChevronForwardOutline" />
        </div>
      </div>
    </div>

    <div class="tabs" :class="activeTab">
      <div class="tab-slider"></div>
      <div class="tab-item" :class="{ active: activeTab === 'friends' }" @click="activeTab = 'friends'">好友</div>
      <div class="tab-item" :class="{ active: activeTab === 'groups' }" @click="activeTab = 'groups'">群聊</div>
    </div>

    <div class="list-container">
      <!-- 加载中骨架屏 -->
      <template v-if="isLoading">
        <div class="skeleton-item" v-for="i in 8" :key="i">
          <n-skeleton circle size="large" class="skeleton-avatar" />
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

      <!-- 列表 -->
      <template v-else>
        <div v-if="activeTab === 'friends'" class="contacts-list">
          <div class="group-header">
            <span class="group-name">我的好友</span>
            <span class="group-count">{{ friendGroups[0].online }}/{{ friendGroups[0].total }}</span>
          </div>
          <div
            v-for="item in friendGroups[0].items"
            :key="item.id"
            class="contact-row"
            :class="{ active: currentSessionId === item.id }"
            @click="handleContactClick(item)"
          >
            <Avatar :text="item.avatarText" :color="item.avatarColor" :size="46" />
            <div class="info">
              <div class="name-row">
                <span class="name">{{ item.name }}</span>
                <span class="status-dot" :class="{ online: item.online }"></span>
              </div>
              <span class="status">{{ item.online ? '在线' : '离线' }}</span>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'groups'" class="groups-list">
          <div v-for="group in chatGroups" :key="group.name" class="group-section">
            <div class="group-header">
              <span class="group-name">{{ group.name }}</span>
              <span class="group-count">{{ group.total }}</span>
            </div>
          </div>
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
  background: #f0f0f0;
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
  background: #f7f7f7;
}

.action-item.active {
  background: rgba(18, 183, 245, 0.08);
  color: var(--lx-accent);
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
  color: #888;
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
  background: #f7f7f7;
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
  color: #888;
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
