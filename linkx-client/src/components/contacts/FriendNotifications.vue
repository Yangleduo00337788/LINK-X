<script setup lang="ts">
import { onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { FilterOutline, TrashOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../../stores/notifications'
import { useContactsStore } from '../../stores/contacts'
import { useAppStore } from '../../stores/app'

const message = useMessage()
const notificationsStore = useNotificationsStore()
const contactsStore = useContactsStore()
const appStore = useAppStore()

const { friendNotifs, loading } = storeToRefs(notificationsStore)
const { fetchFriendRequests, acceptFriendRequest, rejectFriendRequest, clearFriendNotifs } =
  notificationsStore
const { fetchFriends } = contactsStore
const { addFriendSession } = appStore

onMounted(() => {
  void fetchFriendRequests()
})

async function handleAccept(id: string) {
  const n = notificationsStore.findFriendNotif(id)
  if (!n || n.status !== '等待验证' || n.direction !== 'incoming') return

  try {
    const accepted = await acceptFriendRequest(n.requestId)
    await fetchFriends()
    if (accepted) {
      addFriendSession({
        userId: accepted.peerUserId,
        name: accepted.name,
        avatarUrl: accepted.avatar
      })
    }
    message.success(`已同意「${n.name}」的好友请求`)
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '处理好友申请失败')
  }
}

async function handleReject(id: string) {
  const n = notificationsStore.findFriendNotif(id)
  if (!n || n.status !== '等待验证' || n.direction !== 'incoming') return

  try {
    await rejectFriendRequest(n.requestId)
    message.success('已拒绝好友请求')
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '处理好友申请失败')
  }
}

function handleClear() {
  clearFriendNotifs()
  message.success('已清空通知')
}
</script>

<template>
  <div class="notifications-view">
    <div class="header">
      <h2 class="title">好友通知</h2>
      <div class="actions">
        <button class="action-btn" title="清空" @click="handleClear">
          <n-icon :component="TrashOutline" :size="20" />
        </button>
        <button class="action-btn" title="筛选">
          <n-icon :component="FilterOutline" :size="20" />
        </button>
      </div>
    </div>
    <div class="content">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="!friendNotifs.length" class="empty">暂无好友通知</div>
      <div v-else class="notif-list">
        <div v-for="item in friendNotifs" :key="item.id" class="notif-card">
          <img :src="item.avatar" class="avatar" alt="" />
          <div class="info">
            <div class="title-line">
              <span class="name">{{ item.name }}</span>
              <span class="action-text">{{ item.action }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">留言: {{ item.message || '无' }}</div>
            <div v-if="item.source" class="source">来源: {{ item.source }}</div>
          </div>
          <div
            v-if="item.status === '等待验证' && item.direction === 'incoming'"
            class="actions-right"
          >
            <button type="button" class="btn accept" @click="handleAccept(item.id)">同意</button>
            <button type="button" class="btn reject" @click="handleReject(item.id)">拒绝</button>
          </div>
          <div v-else class="status">{{ item.status }}</div>
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

.avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-avatar-radius);
  flex-shrink: 0;
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
