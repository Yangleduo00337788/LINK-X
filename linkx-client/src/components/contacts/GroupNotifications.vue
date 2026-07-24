<script setup lang="ts">
/**
 * 群通知视图。
 * <p>
 * 1) 群邀请：{@code GET /group/invitations}
 * 2) 入群申请（管理员）：{@code messageNotifs} 中 type=group_join_request
 * </p>
 */
import { computed, onMounted, ref } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { FilterOutline, RefreshOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../../stores/notifications'
import { useAppStore } from '../../stores/app'
import * as groupApi from '../../api/group'
import { useI18n } from '../../i18n'

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
  markMessageAsRead
} = notificationsStore
const submitting = ref(false)
const joinHandlingId = ref<string | null>(null)

const joinRequestNotifs = computed(() =>
  messageNotifs.value.filter(n => n.type === 'group_join_request' && n.readStatus === 0)
)

onMounted(() => {
  void fetchGroupInvitations()
  void fetchMessageNotifications()
})

function statusLabel(status: string) {
  if (status === '等待验证') return t('contacts.waiting')
  if (status === '已同意') return t('contacts.accepted')
  if (status === '已拒绝') return t('contacts.rejected')
  return status
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
  await Promise.all([fetchGroupInvitations(), fetchMessageNotifications()])
  message.success(t('contacts.refreshed'))
}

const isEmpty = computed(() => !groupNotifs.value.length && !joinRequestNotifs.value.length)
</script>

<template>
  <div class="notifications-view">
    <div class="header">
      <h2 class="title">{{ t('contacts.groupNotif') }}</h2>
      <div class="actions">
        <button class="action-btn" :title="t('contacts.refresh')" @click="handleClear">
          <n-icon :component="RefreshOutline" :size="20" />
        </button>
        <button class="action-btn" :title="t('contacts.filter')">
          <n-icon :component="FilterOutline" :size="20" />
        </button>
      </div>
    </div>
    <div class="content">
      <div v-if="isEmpty" class="empty">{{ t('contacts.emptyGroupNotif') }}</div>
      <div v-else class="notif-list">
        <div v-for="item in joinRequestNotifs" :key="'jr-' + item.id" class="notif-card">
          <div class="group-icon">申</div>
          <div class="info">
            <div class="title-line">
              <span class="name">{{ t('modals.joinRequests') }}</span>
              <span class="date">{{ item.createTime }}</span>
            </div>
            <div class="message">
              {{ item.senderName }}：{{ item.content || t('modals.joinRequests') }}
            </div>
          </div>
          <div class="actions-right">
            <button
              type="button"
              class="btn accept"
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
            </button>
            <button
              type="button"
              class="btn reject"
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
            </button>
          </div>
        </div>

        <div v-for="item in groupNotifs" :key="item.id" class="notif-card">
          <div class="group-icon">{{ t('contacts.groups').charAt(0) }}</div>
          <div class="info">
            <div class="title-line">
              <span class="name">{{ item.groupName }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">{{ item.inviter }}：{{ item.message }}</div>
          </div>
          <div v-if="item.status === '等待验证'" class="actions-right">
            <button type="button" class="btn accept" @click="handleAccept(item.id)">
              {{ t('contacts.accept') }}
            </button>
            <button type="button" class="btn reject" @click="handleReject(item.id)">
              {{ t('contacts.reject') }}
            </button>
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

.action-btn:hover {
  background: var(--lx-bg-hover);
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
  gap: 8px;
  margin-bottom: 4px;
}

.name {
  font-weight: 600;
  color: var(--lx-text-body);
}

.date {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.message {
  font-size: 13px;
  color: var(--lx-text-secondary);
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

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
