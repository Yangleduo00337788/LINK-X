<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon, NCollapse, NCollapseItem, NSkeleton } from 'naive-ui'
import { ChevronForwardOutline, PersonAddOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import EmptyState from './common/EmptyState.vue'
import { contacts } from '../data/mockData'
import { sessionFromContact } from '../data/mockData'
import { useAppState } from '../composables/useAppState'
import { useChatModals } from '../composables/useChatModals'
import type { ContactItem } from '../types'

const { ensureSession, contactsActiveView, currentSessionId, isLoading } = useAppState()
const { openCreateGroup, openComprehensiveSearch, openContactProfile } = useChatModals()
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
        <n-collapse v-if="activeTab === 'friends'" :default-expanded-names="['我的好友']" accordion class="custom-collapse">
          <n-collapse-item v-for="group in friendGroups" :key="group.name" :name="group.name">
          <template #header>
            <div class="collapse-header">
              <span class="group-name">{{ group.name }}</span>
              <span class="group-count">{{ group.online }}/{{ group.total }}</span>
            </div>
          </template>
          <div class="contact-list" v-if="group.items.length > 0">
            <div
              v-for="item in group.items"
              :key="item.id"
              class="contact-row"
              :class="{ active: currentSessionId === item.id }"
              @click="handleContactClick(item)"
            >
              <Avatar :text="item.avatarText" :color="item.avatarColor" :size="44" />
              <div class="info">
                <span class="name">{{ item.name }}</span>
                <span class="status">{{ item.online ? '在线' : '离线' }}</span>
              </div>
            </div>
          </div>
        </n-collapse-item>
      </n-collapse>

        <n-collapse v-if="activeTab === 'groups'" accordion class="custom-collapse">
          <n-collapse-item v-for="group in chatGroups" :key="group.name" :name="group.name">
            <template #header>
              <div class="collapse-header">
                <span class="group-name">{{ group.name }}</span>
                <span class="group-count">{{ group.total }}</span>
              </div>
            </template>
          </n-collapse-item>
        </n-collapse>
      </template>
    </div>
  </div>
</template>

<style scoped>
.contacts-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel, #f3f3f3);
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
  border-radius: 4px;
  background: #f0f0f0;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #666;
}

.add-btn:hover {
  background: #e8e8e8;
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
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
}

.action-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

.action-item.active {
  background: rgba(18, 183, 245, 0.14);
  color: #12b7f5;
}

.tabs {
  display: flex;
  position: relative;
  margin: 16px 12px 8px;
  background: #ebebeb;
  border-radius: 9px;
  padding: 2px;
}

.tab-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: calc(50% - 2px);
  height: calc(100% - 4px);
  background: #ffffff;
  border-radius: 7px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  z-index: 1;
}

.tabs.groups .tab-slider {
  transform: translateX(100%);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  border-radius: 7px;
  position: relative;
  z-index: 2;
  transition: color 0.3s ease;
}

.tab-item.active {
  color: #1a1a1a;
  font-weight: 500;
}

.list-container {
  flex: 1;
  overflow-y: auto;
}

.collapse-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 12px;
  font-size: 13px;
  color: #333;
}

.group-count {
  color: #999;
  font-size: 12px;
}

:deep(.n-collapse-item__header) {
  padding: 12px 8px !important;
}

:deep(.n-collapse-item__header-main) {
  flex: 1;
}

.contact-row {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 10px 0 12px;
  margin: 0 6px;
  gap: 12px;
  border-radius: var(--lx-radius-md, 10px);
  cursor: pointer;
  transition: background 0.16s ease;
}

.contact-row:hover {
  background: rgba(0, 0, 0, 0.04);
}

.contact-row.active {
  background: rgba(18, 183, 245, 0.14);
}

.info {
  display: flex;
  flex-direction: column;
}

.name {
  font-size: 14px;
  color: #333;
}

.status {
  font-size: 11px;
  color: var(--lx-text-muted, #999);
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
