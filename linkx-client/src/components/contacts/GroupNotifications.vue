<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群通知视图。
 * <p>
 * 1) 群邀请：{@code GET /group/invitations}
 * 2) 入群申请（管理员）：{@code messageNotifs} 中 type=group_join_request
 * </p>
 */
import { computed, onMounted, ref } from 'vue'
import { NIcon, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import { FilterOutline, TrashOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../../stores/notifications'
import type { GroupNotification, MessageNotification } from '../../stores/notifications'
import { useAppStore } from '../../stores/app'
import * as groupApi from '../../api/group'
import { INVITE_STATUS } from '../../types/inviteStatus'
import type { InviteStatus } from '../../types/inviteStatus'
import { useI18n } from '../../i18n'
import { LxButton, LxIconButton } from '../ui'

type FilterKind = 'all' | 'invitation' | 'join_request'
type FilterStatus = 'all' | InviteStatus

const message = useMessage()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const appStore = useAppStore()

const { groupNotifs, messageNotifs } = storeToRefs(notificationsStore)
const {
  fetchGroupInvitations,
  fetchMessageNotifications,
  acceptGroupInvitationAction,
  rejectGroupInvitationAction,
  markMessageAsRead,
  clearGroupNotifsRemote
} = notificationsStore

const submitting = ref(false)
const joinHandlingId = ref<string | null>(null)
const loading = ref(false)
const clearing = ref(false)
const filterKind = ref<FilterKind>('all')
const filterStatus = ref<FilterStatus>('all')

const allJoinRequests = computed(() =>
  messageNotifs.value.filter(n => n.type === 'group_join_request')
)

const hasActiveFilter = computed(
  () => filterKind.value !== 'all' || filterStatus.value !== 'all'
)

const filteredJoinRequests = computed(() =>
  allJoinRequests.value.filter(item => matchesJoinRequestFilter(item))
)

const filteredInvitations = computed(() =>
  groupNotifs.value.filter(item => matchesInvitationFilter(item))
)

const hasAnyNotifs = computed(
  () => groupNotifs.value.length > 0 || allJoinRequests.value.length > 0
)

const hasVisibleNotifs = computed(
  () => filteredJoinRequests.value.length > 0 || filteredInvitations.value.length > 0
)

const clearableCount = computed(() => {
  const invitations = groupNotifs.value.filter(n => n.status !== INVITE_STATUS.PENDING).length
  const joinRead = allJoinRequests.value.filter(n => n.readStatus !== 0).length
  return invitations + joinRead
})

const filterOptions = computed<DropdownOption[]>(() => [
  { label: t('contacts.filterAll'), key: 'reset' },
  { type: 'divider', key: 'd1' },
  { label: t('contacts.filterInvitation'), key: 'kind:invitation' },
  { label: t('contacts.filterJoinRequest'), key: 'kind:join_request' },
  { type: 'divider', key: 'd2' },
  { label: t('contacts.waiting'), key: 'status:pending' },
  { label: t('contacts.accepted'), key: 'status:accepted' },
  { label: t('contacts.rejected'), key: 'status:rejected' },
  { label: t('contacts.expired'), key: 'status:expired' }
])

onMounted(() => {
  void reload()
})

async function reload() {
  loading.value = true
  try {
    await Promise.all([fetchGroupInvitations(), fetchMessageNotifications()])
  } finally {
    loading.value = false
  }
}

function matchesInvitationFilter(item: GroupNotification) {
  if (filterKind.value !== 'all' && filterKind.value !== 'invitation') return false
  if (filterStatus.value === 'all') return true
  return item.status === filterStatus.value
}

function matchesJoinRequestFilter(item: MessageNotification) {
  if (filterKind.value !== 'all' && filterKind.value !== 'join_request') return false
  if (filterStatus.value === 'all') return true
  if (filterStatus.value === INVITE_STATUS.PENDING) return item.readStatus === 0
  return false
}

function handleFilterSelect(key: string | number) {
  const k = String(key)
  if (k === 'reset') {
    filterKind.value = 'all'
    filterStatus.value = 'all'
    return
  }
  if (k.startsWith('kind:')) {
    filterKind.value = k.slice(5) as FilterKind
    return
  }
  if (k.startsWith('status:')) {
    filterStatus.value = k.slice(7) as FilterStatus
  }
}

function statusLabel(status: InviteStatus | string) {
  if (status === INVITE_STATUS.PENDING) return t('contacts.waiting')
  if (status === INVITE_STATUS.ACCEPTED) return t('contacts.accepted')
  if (status === INVITE_STATUS.REJECTED) return t('contacts.rejected')
  if (status === INVITE_STATUS.EXPIRED) return t('contacts.expired')
  return String(status)
}

function joinRequestStatusLabel(item: MessageNotification) {
  return item.readStatus === 0 ? t('contacts.waiting') : t('contacts.processed')
}

async function handleAccept(id: string) {
  if (submitting.value) return
  submitting.value = true
  try {
    await acceptGroupInvitationAction(id)
    message.success(t('contacts.joinedGroup'))
    void appStore.loadChatSessions()
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('contacts.joinFail'))
  } finally {
    submitting.value = false
  }
}

async function handleReject(id: string) {
  if (submitting.value) return
  submitting.value = true
  try {
    await rejectGroupInvitationAction(id)
    message.success(t('contacts.rejectInvite'))
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('contacts.rejectFail'))
  } finally {
    submitting.value = false
  }
}

async function handleJoinRequest(notifId: string, conversationId: string, applicantId: string, approve: boolean) {
  if (!conversationId || !applicantId || joinHandlingId.value) return
  joinHandlingId.value = notifId
  try {
    const res = await groupApi.handleJoinRequest(conversationId, applicantId, approve)
    if (res.code !== 200) {
      throw new Error(res.message || t('modals.joinHandleFail'))
    }
    message.success(approve ? t('modals.joinApproved') : t('modals.joinRejected'))
    await markMessageAsRead(notifId)
    await fetchMessageNotifications()
    if (approve) void appStore.loadChatSessions()
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('modals.joinHandleFail'))
  } finally {
    joinHandlingId.value = null
  }
}

async function handleClear() {
  if (clearing.value) return
  if (clearableCount.value === 0) {
    message.info(t('contacts.nothingToClearGroup'))
    return
  }
  const ok = window.confirm(t('contacts.clearGroupConfirm', { n: clearableCount.value }))
  if (!ok) return

  clearing.value = true
  try {
    const cleared = await clearGroupNotifsRemote()
    if (cleared > 0) {
      message.success(t('contacts.clearedGroupCount', { n: cleared }))
    } else {
      message.info(t('contacts.nothingToClearGroup'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('errors.clearGroupNotificationsFailed'))
  } finally {
    clearing.value = false
  }
}
</script>

<template>
  <div class="notifications-view">
    <div class="header">
      <h2 class="title">{{ t('contacts.groupNotif') }}</h2>
      <div class="actions">
        <LxIconButton
          :title="t('contacts.clear')"
          :disabled="clearing"
          @click="handleClear"
        >
          <n-icon :component="TrashOutline" :size="20" />
        </LxIconButton>
        <n-dropdown trigger="click" :options="filterOptions" @select="handleFilterSelect">
          <LxIconButton
            :active="hasActiveFilter"
            :title="t('contacts.filter')"
          >
            <n-icon :component="FilterOutline" :size="20" />
          </LxIconButton>
        </n-dropdown>
      </div>
    </div>
    <div class="content">
      <div v-if="loading" class="empty">{{ t('common.loading') }}</div>
      <div v-else-if="!hasAnyNotifs" class="empty">{{ t('contacts.emptyGroupNotif') }}</div>
      <div v-else-if="!hasVisibleNotifs" class="empty">{{ t('contacts.emptyGroupNotifFilter') }}</div>
      <div v-else class="notif-list">
        <div v-for="item in filteredJoinRequests" :key="'jr-' + item.id" class="notif-card">
          <div class="group-icon">{{ t('contacts.joinApplyShort') }}</div>
          <div class="info">
            <div class="title-line">
              <span class="name">{{ t('modals.joinRequests') }}</span>
              <span class="date">{{ item.createTime }}</span>
            </div>
            <div class="message">
              {{ item.senderName }}：{{ item.content || t('modals.joinRequests') }}
            </div>
          </div>
          <div
            v-if="item.readStatus === 0"
            class="actions-right"
          >
            <LxButton
              variant="notif-accept"
              :disabled="joinHandlingId === item.id"
              @click="
                handleJoinRequest(
                  item.id,
                  String(item.relatedId || ''),
                  String(item.senderId || ''),
                  true
                )
              "
            >
              {{ t('modals.joinApprove') }}
            </LxButton>
            <LxButton
              variant="notif-reject"
              :disabled="joinHandlingId === item.id"
              @click="
                handleJoinRequest(
                  item.id,
                  String(item.relatedId || ''),
                  String(item.senderId || ''),
                  false
                )
              "
            >
              {{ t('modals.joinReject') }}
            </LxButton>
          </div>
          <div v-else class="status">{{ joinRequestStatusLabel(item) }}</div>
        </div>

        <div v-for="item in filteredInvitations" :key="item.id" class="notif-card">
          <div class="group-icon">{{ t('contacts.groups').charAt(0) }}</div>
          <div class="info">
            <div class="title-line">
              <span class="name">{{ item.groupName }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">{{ item.inviter }}：{{ item.message }}</div>
          </div>
          <div v-if="item.status === INVITE_STATUS.PENDING" class="actions-right">
            <LxButton variant="notif-accept" :disabled="submitting" @click="handleAccept(item.id)">
              {{ t('contacts.accept') }}
            </LxButton>
            <LxButton variant="notif-reject" :disabled="submitting" @click="handleReject(item.id)">
              {{ t('contacts.reject') }}
            </LxButton>
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
  padding: 0 var(--lx-space-4xl);
  border-bottom: 1px solid var(--lx-divider);
}

.title {
  font-size: var(--lx-font-3xl);
  font-weight: 500;
  color: var(--lx-text-body);
  margin: 0;
}

.actions {
  display: flex;
  gap: var(--lx-space);
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-2xl) var(--lx-space-4xl);
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: var(--lx-space-section);
}

.notif-list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
}

.notif-card {
  display: flex;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-2xl);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  align-items: flex-start;
}

.group-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-avatar-radius);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
}

.title-line {
  display: flex;
  justify-content: space-between;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space-xs);
}

.name {
  font-weight: 600;
  color: var(--lx-text-body);
}

.date {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.message {
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
}

.actions-right {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
  flex-shrink: 0;
}

.status {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  flex-shrink: 0;
}
</style>
