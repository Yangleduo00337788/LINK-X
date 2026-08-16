<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊右侧边栏（主聊天区内嵌）。
 * <p>
 * 展示群公告摘要与成员列表，支持成员搜索；
 * 左侧中部提供折叠按钮，可收起整块侧栏以扩大聊天区。
 * </p>
 */
import { ref, computed } from 'vue'
import { NIcon, NVirtualList, useDialog, useMessage } from 'naive-ui'
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
import { LxIconButton } from '../ui'
import { virtualListScrollbarProps, useNaiveVirtualListNativeScrollbar } from '../../utils/virtualListScrollbar'

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
const memberListRef = ref<InstanceType<typeof NVirtualList> | null>(null)
useNaiveVirtualListNativeScrollbar(memberListRef)
// 是否显示成员搜索框
const showMemberSearch = ref(false)
/** 侧栏是否折叠（记住用户偏好） */
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

/** 当前群公告短文本 */
const announcementText = computed(() => {
  const id = currentSessionId.value
  if (!id) return t('extra.noAnnouncement')
  const text = groupMetaStore.announcementShort(id)
  return text || t('extra.noAnnouncement')
})

const announcementIsEmpty = computed(() => {
  const id = currentSessionId.value
  if (!id) return true
  return !groupMetaStore.announcementShort(id)
})

/** 当前群全部成员（只读 store，避免在 computed 里反复触发 fetch） */
const members = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.members[id] || []
})

/** 成员与公告由 ChatPanel 统一预加载，侧栏只读缓存 */

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

/** 群主或管理员 */
const isAdminOrOwner = computed(() => {
  const me = userProfile.value.userId
  if (!me) return false
  return members.value.some(m => m.id === me && (m.role === 'owner' || m.role === 'admin'))
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

function canManageMember(m: GroupMember): boolean {
  const me = userProfile.value.userId
  if (!me || m.id === me || m.role === 'owner') return false
  if (isOwner.value) return true
  if (isAdminOrOwner.value && m.role !== 'admin') return true
  return false
}

/** 群主/管理员点击成员：禁言或解除禁言 */
function onMemberClick(m: GroupMember) {
  if (!currentSessionId.value || !canManageMember(m)) return

  const muted = !!m.muted
  dialog.warning({
    title: muted ? t('modals.unmuteMember') : t('modals.muteMember'),
    content: muted
      ? t('modals.unmuteMemberConfirm', { name: m.name })
      : t('modals.muteMemberConfirm', { name: m.name }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await groupMetaStore.setMemberMute(currentSessionId.value!, m.id, !muted)
        message.success(muted ? t('modals.unmuteMemberOk') : t('modals.muteMemberOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.muteMemberFail')))
      }
    }
  })
}
</script>

<template>
  <!-- 群聊会话右侧固定边栏（可折叠） -->
  <aside class="group-side" :class="{ collapsed }">
    <!-- 折叠态：右侧细条命中区，悬停后才露出按钮 -->
    <div class="collapse-hover-zone" aria-hidden="true" />
    <!-- 左缘中部折叠按钮：默认隐藏，侧栏/命中区悬停时显示 -->
    <button
      type="button"
      class="lx-collapse-handle"
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
          <button type="button" class="lx-link-btn lx-link-btn--plain" :title="t('extra.viewAnnouncement')" @click="openGroupAnnouncement">
            <n-icon :component="ChevronForwardOutline" :size="18" />
          </button>
        </div>
        <button
          type="button"
          class="lx-link-btn lx-link-btn--announce"
          :class="{ 'is-empty': announcementIsEmpty }"
          @click="openGroupAnnouncement"
        >
          {{ announcementText }}
        </button>
      </section>
      <!-- 群成员列表 -->
      <section class="members-block">
        <div class="members-head">
          <span class="side-title">{{ t('extra.groupMembersCount', { n: memberCount }) }}</span>
          <LxIconButton :title="t('extra.searchMembers')" @click="toggleMemberSearch">
            <n-icon :component="showMemberSearch ? CloseOutline : SearchOutline" :size="18" />
          </LxIconButton>
        </div>
        <div v-if="showMemberSearch" class="member-search">
          <input
            v-model="memberSearch"
            type="text"
            class="member-search-input"
            :placeholder="t('extra.searchMembersPh')"
          />
        </div>
        <div v-if="showMemberSearch && !filteredMembers.length" class="member-empty">
          {{ t('extra.noMatchMembers') }}
        </div>
        <n-virtual-list
          v-else
          ref="memberListRef"
          class="member-list"
          :items="filteredMembers"
          :item-size="48"
          item-key="id"
          :scrollbar-props="virtualListScrollbarProps"
        >
          <template #default="{ item }">
            <div
              class="member-row"
              :class="{ clickable: canManageMember(item as GroupMember) }"
              @click="onMemberClick(item as GroupMember)"
            >
              <Avatar
                :text="(item as GroupMember).avatarText"
                :color="(item as GroupMember).avatarColor"
                :image-url="(item as GroupMember).avatarUrl"
                :size="36"
              />
              <div class="m-info">
                <span class="m-name">{{ (item as GroupMember).name }}</span>
                <span v-if="(item as GroupMember).badge" class="m-badge">{{ (item as GroupMember).badge }}</span>
                <span v-else-if="(item as GroupMember).muted" class="m-badge muted-badge">{{ t('modals.mutedBadge') }}</span>
              </div>
            </div>
          </template>
        </n-virtual-list>
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

.collapse-hover-zone {
  position: absolute;
  left: -14px;
  top: 0;
  width: 20px;
  height: 100%;
  z-index: var(--lx-z-raised-4);
}

.group-side.collapsed .collapse-hover-zone {
  left: -18px;
  width: 18px;
}

.group-side.collapsed .lx-collapse-handle {
  left: 0;
  transform: translate(-100%, -50%);
  border-radius: var(--lx-radius-sm) 0 0 var(--lx-radius-sm);
  border-right: none;
}

.announce-block {
  flex-shrink: 0;
  padding: var(--lx-space-xl) var(--lx-space-lg);
  border-bottom: 1px solid var(--lx-bg-panel-deep);
  background: var(--lx-bg-panel);
}

.announce-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--lx-space);
}

.side-title {
  margin: 0;
  font-size: var(--lx-font-md);
  font-weight: 600;
  color: var(--lx-text-body);
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
  padding: var(--lx-space-lg) var(--lx-space-lg) var(--lx-space);
  flex-shrink: 0;
}

.member-search {
  padding: 0 var(--lx-space-lg) var(--lx-space);
  flex-shrink: 0;
}

.member-search-input {
  width: 100%;
  height: 30px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 var(--lx-space-md);
  font-size: var(--lx-font-sm);
  outline: none;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
}

.member-search-input:focus {
  border-color: var(--lx-accent);
}

.member-list {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0 var(--lx-space) var(--lx-space-lg);
}

.member-empty {
  padding: var(--lx-space-2xl) var(--lx-space);
  text-align: center;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: var(--lx-space) var(--lx-space-xs);
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
  gap: var(--lx-space-2xs);
}

.m-name {
  font-size: var(--lx-font-md);
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-badge {
  font-size: var(--lx-font-xs);
  color: var(--lx-accent);
}

.muted-badge {
  color: var(--lx-danger);
}
</style>
