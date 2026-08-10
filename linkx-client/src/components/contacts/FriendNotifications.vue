<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMessage, NIcon, NDropdown, type DropdownOption } from 'naive-ui'
import { FilterOutline, TrashOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../../stores/notifications'
import type { FriendNotification } from '../../stores/notifications'
import { INVITE_STATUS } from '../../types/inviteStatus'
import type { InviteStatus } from '../../types/inviteStatus'
import { useContactsStore } from '../../stores/contacts'
import { useAppStore } from '../../stores/app'
import { useI18n } from '../../i18n'
import Avatar from '../Avatar.vue'

type FilterDirection = 'all' | 'incoming' | 'outgoing'
type FilterStatus = 'all' | InviteStatus

const message = useMessage()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const contactsStore = useContactsStore()
const appStore = useAppStore()

const { friendNotifs, loading } = storeToRefs(notificationsStore)
const { fetchFriendRequests, acceptFriendRequest, rejectFriendRequest, clearFriendNotifsRemote } =
  notificationsStore
const { fetchFriends } = contactsStore
const { addFriendSession } = appStore

const filterDirection = ref<FilterDirection>('all')
const filterStatus = ref<FilterStatus>('all')
const clearing = ref(false)

onMounted(() => {
  void fetchFriendRequests()
})

const hasActiveFilter = computed(
  () => filterDirection.value !== 'all' || filterStatus.value !== 'all'
)

const filteredFriendNotifs = computed(() =>
  friendNotifs.value.filter(item => {
    if (filterDirection.value !== 'all' && item.direction !== filterDirection.value) return false
    if (filterStatus.value !== 'all' && item.status !== filterStatus.value) return false
    return true
  })
)

const clearableCount = computed(
  () => friendNotifs.value.filter(n => n.status !== INVITE_STATUS.PENDING).length
)

const filterOptions = computed<DropdownOption[]>(() => [
  { label: t('contacts.filterAll'), key: 'reset' },
  { type: 'divider', key: 'd1' },
  { label: t('contacts.filterIncoming'), key: 'dir:incoming' },
  { label: t('contacts.filterOutgoing'), key: 'dir:outgoing' },
  { type: 'divider', key: 'd2' },
  { label: t('contacts.waiting'), key: 'status:pending' },
  { label: t('contacts.accepted'), key: 'status:accepted' },
  { label: t('contacts.rejected'), key: 'status:rejected' }
])

function statusLabel(status: InviteStatus | string) {
  if (status === INVITE_STATUS.PENDING) return t('contacts.waiting')
  if (status === INVITE_STATUS.ACCEPTED) return t('contacts.accepted')
  if (status === INVITE_STATUS.REJECTED) return t('contacts.rejected')
  if (status === INVITE_STATUS.EXPIRED) return t('contacts.expired')
  return String(status)
}

function friendActionText(direction: FriendNotification['direction']) {
  return direction === 'incoming'
    ? t('contacts.friendActionIncoming')
    : t('contacts.friendActionOutgoing')
}

function friendMessageText(item: FriendNotification) {
  if (item.message) return item.message
  if (item.direction === 'outgoing') return t('contacts.defaultOutgoingMessage')
  return t('contacts.none')
}

function handleFilterSelect(key: string | number) {
  const k = String(key)
  if (k === 'reset') {
    filterDirection.value = 'all'
    filterStatus.value = 'all'
    return
  }
  if (k.startsWith('dir:')) {
    filterDirection.value = k.slice(4) as FilterDirection
    return
  }
  if (k.startsWith('status:')) {
    filterStatus.value = k.slice(7) as FilterStatus
  }
}

async function handleAccept(id: string) {
  const n = notificationsStore.findFriendNotif(id)
  if (!n || n.status !== INVITE_STATUS.PENDING || n.direction !== 'incoming') return

  try {
    const accepted = await acceptFriendRequest(n.requestId)
    await fetchFriends()
    if (accepted) {
      await addFriendSession({
        userId: accepted.peerUserId,
        name: accepted.name,
        avatarUrl: accepted.avatar
      })
    }
    message.success(t('contacts.acceptedRequest', { name: n.name }))
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('contacts.handleFail'))
  }
}

async function handleReject(id: string) {
  const n = notificationsStore.findFriendNotif(id)
  if (!n || n.status !== INVITE_STATUS.PENDING || n.direction !== 'incoming') return

  try {
    await rejectFriendRequest(n.requestId)
    message.success(t('contacts.rejectedRequest'))
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('contacts.handleFail'))
  }
}

async function handleClear() {
  if (clearing.value) return
  if (clearableCount.value === 0) {
    message.info(t('contacts.nothingToClearFriend'))
    return
  }
  const ok = window.confirm(t('contacts.clearFriendConfirm', { n: clearableCount.value }))
  if (!ok) return

  clearing.value = true
  try {
    const cleared = await clearFriendNotifsRemote()
    if (cleared > 0) {
      message.success(t('contacts.clearedFriendCount', { n: cleared }))
    } else {
      message.info(t('contacts.nothingToClearFriend'))
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('errors.clearFriendRequestsFailed'))
  } finally {
    clearing.value = false
  }
}
</script>

<template>
  <div class="notifications-view">
    <div class="header">
      <h2 class="title">{{ t('contacts.friendNotif') }}</h2>
      <div class="actions">
        <button
          class="action-btn"
          :title="t('contacts.clear')"
          :disabled="clearing"
          @click="handleClear"
        >
          <n-icon :component="TrashOutline" :size="20" />
        </button>
        <n-dropdown trigger="click" :options="filterOptions" @select="handleFilterSelect">
          <button
            class="action-btn"
            :class="{ active: hasActiveFilter }"
            :title="t('contacts.filter')"
          >
            <n-icon :component="FilterOutline" :size="20" />
          </button>
        </n-dropdown>
      </div>
    </div>
    <div class="content">
      <div v-if="loading" class="empty">{{ t('common.loading') }}</div>
      <div v-else-if="!friendNotifs.length" class="empty">{{ t('contacts.emptyFriendNotif') }}</div>
      <div v-else-if="!filteredFriendNotifs.length" class="empty">
        {{ t('contacts.emptyFriendNotifFilter') }}
      </div>
      <div v-else class="notif-list">
        <div v-for="item in filteredFriendNotifs" :key="item.id" class="notif-card">
          <Avatar
            :text="(item.name || '?').charAt(0)"
            color="#12b7f5"
            :image-url="item.avatar || undefined"
            :size="44"
          />
          <div class="info">
            <div class="title-line">
              <span class="name">{{ item.name }}</span>
              <span class="action-text">{{ friendActionText(item.direction) }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">{{ t('contacts.messageLabel', { msg: friendMessageText(item) }) }}</div>
            <div v-if="item.source" class="source">{{ t('contacts.sourceLabel', { source: item.source }) }}</div>
          </div>
          <div
            v-if="item.status === INVITE_STATUS.PENDING && item.direction === 'incoming'"
            class="actions-right"
          >
            <button type="button" class="btn accept" @click="handleAccept(item.id)">{{ t('contacts.accept') }}</button>
            <button type="button" class="btn reject" @click="handleReject(item.id)">{{ t('contacts.reject') }}</button>
          </div>
          <div v-else class="status">{{ statusLabel(item.status) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notifications-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--lx-bg-window, var(--lx-bg-panel));
}

.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--lx-divider);
}

.title {
  font-size: 18px;
  font-weight: 500;
  color: var(--lx-text-body);
  margin: 0;
}

.actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  cursor: pointer;
  color: var(--lx-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
}

.action-btn.active {
  color: var(--lx-accent);
  background: rgba(18, 183, 245, 0.1);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 40px;
}

.notif-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notif-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  align-items: flex-start;
}

.info {
  flex: 1;
  min-width: 0;
}

.title-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  margin-bottom: 4px;
}

.name {
  font-weight: 600;
  color: var(--lx-text-body);
}

.action-text {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.date {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-left: auto;
}

.message,
.source {
  font-size: 13px;
  color: var(--lx-text-secondary);
  margin-top: 2px;
}

.actions-right {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

.btn {
  min-width: 56px;
  height: 28px;
  border-radius: var(--lx-radius);
  border: none;
  font-size: 12px;
  cursor: pointer;
}

.btn.accept {
  background: var(--lx-accent);
  color: var(--lx-bg-card);
}

.btn.reject {
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
}

.status {
  font-size: 12px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}
</style>
