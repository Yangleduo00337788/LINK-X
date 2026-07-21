<script setup lang="ts">
/**
 * 群聊右侧边栏（主聊天区内嵌）。
 * <p>
 * 展示群公告摘要与成员列表，支持成员搜索；
 * 左侧中部提供折叠按钮，可收起整块侧栏以扩大聊天区。
 * </p>
 */
import { ref, computed, watch } from 'vue'
import { NIcon, useDialog, useMessage } from 'naive-ui'
import {
  SearchOutline,
  ChevronForwardOutline,
  ChevronBackOutline,
  CloseOutline
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'
import type { GroupMember } from '../../stores/groupMeta'

const COLLAPSE_KEY = 'linkx.groupSidebar.collapsed'

const { t } = useI18n()
const message = useMessage()
const dialog = useDialog()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { openGroupAnnouncement } = chatModalsStore
const { currentSessionId, userProfile } = storeToRefs(appStore)

// 成员搜索关键词
const memberSearch = ref('')
// 是否显示成员搜索框
const showMemberSearch = ref(false)
/** 侧栏是否折叠（记住用户偏好） */
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

/** 当前群公告短文本 */
const announcementText = computed(() => {
  const id = currentSessionId.value
  return id ? groupMetaStore.announcementShort(id) : ''
})

/** 当前群全部成员（只读 store，避免在 computed 里反复触发 fetch） */
const members = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.members[id] || []
})

watch(
  currentSessionId,
  id => {
    if (id) void groupMetaStore.fetchMembers(id)
  },
  { immediate: true }
)

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

/** 当前用户是否为群主 */
const isOwner = computed(() => {
  const me = userProfile.value.userId
  if (!me) return false
  return members.value.some(m => m.id === me && m.role === 'owner')
})

/** 切换成员搜索框显示，关闭时清空关键词 */
function toggleMemberSearch() {
  showMemberSearch.value = !showMemberSearch.value
  if (!showMemberSearch.value) memberSearch.value = ''
}

/** 折叠 / 展开侧栏 */
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
  if (collapsed.value) {
    showMemberSearch.value = false
    memberSearch.value = ''
  }
}

function apiErrorMessage(e: unknown, fallback: string): string {
  const ax = e as { response?: { data?: { message?: string } }; message?: string }
  return ax.response?.data?.message || ax.message || fallback
}

/** 群主点击成员：设为 / 取消管理员 */
function onMemberClick(m: GroupMember) {
  if (!isOwner.value || !currentSessionId.value) return
  const me = userProfile.value.userId
  if (!me || m.id === me || m.role === 'owner') return

  const isAdmin = m.role === 'admin'
  dialog.warning({
    title: isAdmin ? t('modals.unsetAdmin') : t('modals.setAdmin'),
    content: isAdmin
      ? t('modals.unsetAdminConfirm', { name: m.name })
      : t('modals.setAdminConfirm', { name: m.name }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await appStore.updateMemberRole(
          currentSessionId.value!,
          m.id,
          isAdmin ? 'member' : 'admin'
        )
        message.success(isAdmin ? t('modals.unsetAdminOk') : t('modals.setAdminOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.setAdminFail')))
      }
    }
  })
}
</script>

<template>
  <!-- 群聊会话右侧固定边栏（可折叠） -->
  <aside class="group-side" :class="{ collapsed }">
    <!-- 左缘中部折叠按钮 -->
    <button
      type="button"
      class="collapse-btn"
      :title="collapsed ? t('extra.expandGroupSide') : t('extra.collapseGroupSide')"
      @click="toggleCollapsed"
    >
      <n-icon
        :component="collapsed ? ChevronBackOutline : ChevronForwardOutline"
        :size="14"
      />
    </button>

    <div v-show="!collapsed" class="group-side-body">
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
          <div
            v-for="m in filteredMembers"
            :key="m.id"
            class="member-row"
            :class="{ clickable: isOwner && m.role !== 'owner' && m.id !== userProfile.userId }"
            @click="onMemberClick(m)"
          >
            <Avatar :text="m.avatarText" :color="m.avatarColor" :image-url="m.avatarUrl" :size="36" />
            <div class="m-info">
              <span class="m-name">{{ m.name }}</span>
              <span v-if="m.badge" class="m-badge">{{ m.badge }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.group-side {
  position: relative;
  width: 240px;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: visible;
  transition: width 0.22s ease;
}

.group-side.collapsed {
  width: 0;
  border-left-color: transparent;
  background: transparent;
}

.group-side-body {
  width: 240px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
}

.collapse-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 5;
  width: 16px;
  height: 48px;
  padding: 0;
  border: 1px solid var(--lx-border-light);
  border-radius: 8px;
  background: var(--lx-bg-card);
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px var(--lx-shadow-color);
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}

.collapse-btn:hover {
  color: var(--lx-accent);
  border-color: var(--lx-accent);
  background: var(--lx-bg-card);
}

.group-side.collapsed .collapse-btn {
  left: 0;
  transform: translate(-100%, -50%);
  border-radius: 8px 0 0 8px;
  border-right: none;
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

.member-row.clickable {
  cursor: pointer;
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
