<script setup lang="ts">
/**
 * 群聊右侧边栏（主聊天区内嵌）。
 * <p>
 * 展示群公告摘要与成员列表，支持成员搜索。
 * </p>
 */
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import { SearchOutline, ChevronForwardOutline, CloseOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'

const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { openGroupAnnouncement } = chatModalsStore
const { currentSessionId } = storeToRefs(appStore)

// 成员搜索关键词
const memberSearch = ref('')
// 是否显示成员搜索框
const showMemberSearch = ref(false)

/** 当前群公告短文本 */
const announcementText = computed(() => {
  const id = currentSessionId.value
  return id ? groupMetaStore.announcementShort(id) : ''
})

/** 当前群全部成员 */
const members = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.membersFor(id)
})

/** 按昵称或 badge 过滤后的成员列表 */
const filteredMembers = computed(() => {
  const q = memberSearch.value.trim().toLowerCase()
  if (!q) return members.value
  return members.value.filter(
    m => m.name.toLowerCase().includes(q) || m.badge?.toLowerCase().includes(q)
  )
})

/** 成员总数 */
const memberCount = computed(() => members.value.length)

/** 切换成员搜索框显示，关闭时清空关键词 */
function toggleMemberSearch() {
  showMemberSearch.value = !showMemberSearch.value
  if (!showMemberSearch.value) memberSearch.value = ''
}
</script>

<template>
  <!-- 群聊会话右侧固定边栏 -->
  <aside class="group-side">
    <!-- 群公告区块 -->
    <section class="announce-block">
      <div class="announce-head">
        <h3 class="side-title">{{ t('chat.groupAnnouncement') }}</h3>
        <button type="button" class="arrow-btn" :title="t('extra.viewAnnouncement')" @click="openGroupAnnouncement">
          <n-icon :component="ChevronForwardOutline" :size="18" />
        </button>
      </div>
      <button type="button" class="announce-text-btn" @click="openGroupAnnouncement">
        {{ announcementText }}
      </button>
    </section>
    <!-- 群成员列表 -->
    <section class="members-block">
      <div class="members-head">
        <span class="side-title">{{ t('extra.groupMembersCount', { n: memberCount }) }}</span>
        <button type="button" class="icon-btn" :title="t('extra.searchMembers')" @click="toggleMemberSearch">
          <n-icon :component="showMemberSearch ? CloseOutline : SearchOutline" :size="18" />
        </button>
      </div>
      <div v-if="showMemberSearch" class="member-search">
        <input
          v-model="memberSearch"
          type="text"
          class="member-search-input"
          :placeholder="t('extra.searchMembersPh')"
        />
      </div>
      <div class="member-list">
        <div v-if="showMemberSearch && !filteredMembers.length" class="member-empty">
          {{ t('extra.noMatchMembers') }}
        </div>
        <div v-for="m in filteredMembers" :key="m.id" class="member-row">
          <Avatar :text="m.avatarText" :color="m.avatarColor" :size="36" />
          <div class="m-info">
            <span class="m-name">{{ m.name }}</span>
            <span v-if="m.badge" class="m-badge">{{ m.badge }}</span>
          </div>
        </div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.group-side {
  width: 240px;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.announce-block {
  flex-shrink: 0;
  padding: 14px 12px;
  border-bottom: 1px solid var(--lx-bg-panel-deep);
  background: var(--lx-bg-panel);
}

.announce-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.side-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.arrow-btn,
.icon-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}

.arrow-btn:hover,
.icon-btn:hover {
  color: var(--lx-accent);
}

.announce-text-btn {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--lx-text-secondary);
  word-break: break-all;
  cursor: pointer;
}

.announce-text-btn:hover {
  color: var(--lx-accent);
}

.members-block {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.members-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}

.member-search {
  padding: 0 12px 8px;
  flex-shrink: 0;
}

.member-search-input {
  width: 100%;
  height: 30px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 10px;
  font-size: 12px;
  outline: none;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
}

.member-search-input:focus {
  border-color: var(--lx-accent);
}

.member-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.member-empty {
  padding: 16px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: var(--lx-radius);
}

.member-row:hover {
  background: var(--lx-bg-hover);
}

.m-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.m-name {
  font-size: 13px;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-badge {
  font-size: 11px;
  color: var(--lx-accent);
}
</style>
