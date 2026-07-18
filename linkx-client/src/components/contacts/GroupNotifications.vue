<script setup lang="ts">
/**
 * 群通知视图。
 * <p>
 * 真实调用 {@code GET /group/invitations} 拉取；接受/拒绝通过
 * {@code useNotificationsStore.acceptGroupInvitationAction / rejectGroupInvitationAction}。
 * 接受成功后由上层跳转到对应群会话。
 * </p>
 */
import { onMounted, ref } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { FilterOutline, RefreshOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../../stores/notifications'
import { useAppStore } from '../../stores/app'

const message = useMessage()
const notificationsStore = useNotificationsStore()
const appStore = useAppStore()

const { groupNotifs } = storeToRefs(notificationsStore)
const { fetchGroupInvitations, acceptGroupInvitationAction, rejectGroupInvitationAction } =
  notificationsStore
const submitting = ref(false)

onMounted(() => {
  void fetchGroupInvitations()
})

async function handleAccept(id: string) {
  if (submitting.value) return
  submitting.value = true
  try {
    await acceptGroupInvitationAction(id)
    message.success('已加入群聊')
    // 刷新会话列表（接受后服务端已写入会话成员）
    void appStore.loadChatSessions()
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加入群聊失败')
  } finally {
    submitting.value = false
  }
}

async function handleReject(id: string) {
  if (submitting.value) return
  submitting.value = true
  try {
    await rejectGroupInvitationAction(id)
    message.success('已拒绝群邀请')
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '拒绝群邀请失败')
  } finally {
    submitting.value = false
  }
}

async function handleClear() {
  // 仅刷新本地视图；不接受/拒绝的邀请后端仍保留，待用户处理。
  await fetchGroupInvitations()
  message.success('已刷新')
}
</script>

<template>
  <!-- 群通知主视图 -->
  <div class="notifications-view">
    <!-- 顶部标题与操作栏 -->
    <div class="header">
      <h2 class="title">群通知</h2>
      <div class="actions">
        <button class="action-btn" title="刷新" @click="handleClear">
          <n-icon :component="RefreshOutline" :size="20" />
        </button>
        <button class="action-btn" title="筛选">
          <n-icon :component="FilterOutline" :size="20" />
        </button>
      </div>
    </div>
    <!-- 通知列表内容区 -->
    <div class="content">
      <div v-if="!groupNotifs.length" class="empty">暂无群通知</div>
      <div v-else class="notif-list">
        <div v-for="item in groupNotifs" :key="item.id" class="notif-card">
          <div class="group-icon">群</div>
          <div class="info">
            <div class="title-line">
              <span class="name">{{ item.groupName }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">{{ item.inviter }}：{{ item.message }}</div>
          </div>
          <div v-if="item.status === '等待验证'" class="actions-right">
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
