<script setup lang="ts">
/**
 * 综合搜索模态框。
 * <p>
 * 在本地联系人与群会话中按关键词搜索，支持加好友与进入群聊。
 * </p>
 */
import { ref, computed } from 'vue'
import Avatar from '../Avatar.vue'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const { comprehensiveSearchOpen } = storeToRefs(chatModalsStore)
const { closeComprehensiveSearch } = chatModalsStore
const { groupSessions } = storeToRefs(appStore)
const { joinGroup: joinGroupAction, addFriendSession: addFriendAction } = appStore

// 搜索关键词
const keyword = ref('')
// 主 Tab：全部 / 用户 / 群聊
const mainTab = ref<'all' | 'user' | 'group'>('all')
// 是否已执行过至少一次搜索
const searched = ref(false)

// Tab 配置项
const mainTabs = [
  { key: 'all', label: '全部' },
  { key: 'user', label: '用户' },
  { key: 'group', label: '群聊' }
] as const

/** 关闭模态并重置搜索状态 */
function close() {
  closeComprehensiveSearch()
  searched.value = false
}

/** 触发搜索：关键词为空则提示 */
function doSearch() {
  if (!keyword.value.trim()) {
    message.warning('请输入关键词')
    return
  }
  searched.value = true
}

/** 匹配的用户列表（需已 searched） */
const filteredUsers = computed(() => {
  if (!searched.value) return []
  return contactsStore.searchUsers(keyword.value)
})

/** 匹配的群会话列表 */
const filteredGroups = computed(() => {
  if (!searched.value) return []
  const q = keyword.value.trim().toLowerCase()
  return groupSessions.value.filter(s => s.name.toLowerCase().includes(q))
})

/** 当前 Tab 是否展示群聊结果 */
const showGroups = computed(() => mainTab.value === 'all' || mainTab.value === 'group')
/** 当前 Tab 是否展示用户结果 */
const showUsers = computed(() => mainTab.value === 'all' || mainTab.value === 'user')

/** 按群名加入/进入群聊 */
function joinGroupByName(name: string) {
  const session = joinGroupAction(name)
  message.success(`已加入群聊「${session.name}」`)
  close()
}

/** 按用户名添加好友会话 */
function addFriendByName(name: string) {
  const session = addFriendAction(name)
  message.success(`已添加好友「${session.name}」`)
  close()
}
</script>

<template>
  <!-- 综合搜索全屏模态 -->
  <Teleport to="body">
    <div v-if="comprehensiveSearchOpen" class="modal-root" @click.self="close">
      <div class="search-window" @click.stop>
        <!-- 标题与搜索栏 -->
        <header class="win-title">综合搜索</header>
        <div class="search-row">
          <input
            v-model="keyword"
            type="text"
            class="search-input"
            placeholder="输入搜索关键词"
            @keydown.enter="doSearch"
          />
          <button type="button" class="search-btn" @click="doSearch">搜索</button>
        </div>
        <!-- 结果分类 Tab -->
        <div class="main-tabs">
          <button
            v-for="t in mainTabs"
            :key="t.key"
            type="button"
            class="main-tab"
            :class="{ active: mainTab === t.key }"
            @click="mainTab = t.key"
          >
            {{ t.label }}
          </button>
        </div>
        <!-- 搜索结果列表 -->
        <div class="result-list">
          <template v-if="!searched">
            <p class="empty-tip">输入关键词后搜索本地联系人与群聊</p>
          </template>
          <template v-else>
            <!-- 用户结果区 -->
            <template v-if="showUsers">
              <h4 v-if="filteredUsers.length" class="section-label">用户</h4>
              <article v-for="u in filteredUsers" :key="u.id" class="group-card user-card">
                <Avatar :text="u.avatarText" :color="u.avatarColor" :size="48" />
                <div class="g-body">
                  <h3 class="g-name">{{ u.name }}</h3>
                  <p class="g-meta"><span>{{ u.online ? '在线' : '离线' }}</span></p>
                </div>
                <button type="button" class="join-btn" @click="addFriendByName(u.name)">加好友</button>
              </article>
              <p v-if="showUsers && !filteredUsers.length && mainTab !== 'group'" class="empty-tip">未找到匹配用户</p>
            </template>
            <!-- 群聊结果区 -->
            <template v-if="showGroups">
              <h4 v-if="filteredGroups.length" class="section-label">群聊</h4>
              <article v-for="g in filteredGroups" :key="g.id" class="group-card">
                <div class="g-avatar">{{ g.avatarText.charAt(0) }}</div>
                <div class="g-body">
                  <h3 class="g-name">{{ g.name }}</h3>
                  <p class="g-meta">
                    <span>{{ g.lastMessage || '暂无消息' }}</span>
                  </p>
                </div>
                <button type="button" class="join-btn" @click="joinGroupByName(g.name)">进入</button>
              </article>
              <p v-if="showGroups && !filteredGroups.length && mainTab !== 'user'" class="empty-tip">未找到匹配群聊</p>
            </template>
          </template>
        </div>
        <button type="button" class="close-fab" title="关闭" @click="close">×</button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2150;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.search-window {
  position: relative;
  width: min(920px, 96vw);
  height: min(640px, 90vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 16px 56px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.win-title {
  margin: 0;
  padding: 16px 20px 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.search-row {
  display: flex;
  gap: 10px;
  padding: 8px 20px 12px;
}

.search-input {
  flex: 1;
  height: 36px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 14px;
  font-size: 14px;
  outline: none;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
}

.search-input:focus {
  border-color: var(--lx-accent);
}

.search-btn {
  min-width: 72px;
  height: 36px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 14px;
  cursor: pointer;
}

.main-tabs {
  display: flex;
  gap: 24px;
  padding: 0 20px;
  border-bottom: 1px solid var(--lx-border-light);
}

.main-tab {
  border: none;
  background: none;
  padding: 10px 0;
  font-size: 14px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  position: relative;
}

.main-tab.active {
  color: var(--lx-accent);
  font-weight: 600;
}

.main-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--lx-accent);
  border-radius: 1px;
}

.result-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px 20px;
}

.section-label {
  margin: 12px 0 8px;
  font-size: 13px;
  color: var(--lx-text-muted);
  font-weight: 600;
}

.empty-tip {
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.user-card .g-avatar {
  display: none;
}

.group-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.g-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, var(--lx-accent-light), var(--lx-accent));
  color: var(--lx-bg-card);
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.g-body {
  flex: 1;
  min-width: 0;
}

.g-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.g-meta {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.join-btn {
  flex-shrink: 0;
  min-width: 64px;
  height: 32px;
  border: 1px solid var(--lx-accent);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-card);
  color: var(--lx-accent);
  font-size: 13px;
  cursor: pointer;
}

.join-btn:hover {
  background: var(--lx-accent-soft);
}

.close-fab {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
  line-height: 1;
}

.close-fab:hover {
  color: var(--lx-text-body);
}
</style>
