<script setup lang="ts">
import { NIcon, NButton } from 'naive-ui'
import { FilterOutline, TrashOutline } from '@vicons/ionicons5'

const notifications = [
  {
    id: 1,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=hy',
    name: '寒夜慢慢',
    date: '星期六 23:34',
    message: '邀请你加入 速云17聚合ai模型api调用 claude,gpt,grok,deepseek,veo,gemi...',
    actionBtn: '发消息'
  },
  {
    id: 2,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=morning',
    name: 'MORNING',
    date: '2026/06/28 16:54',
    message: '邀请你加入 极客软件园A12',
    actionBtn: '发消息'
  }
]
</script>

<template>
  <div class="notifications-view">
    <div class="header">
      <h2 class="title">群通知</h2>
      <div class="actions">
        <button class="action-btn">
          <n-icon :component="FilterOutline" :size="20" />
        </button>
        <button class="action-btn">
          <n-icon :component="TrashOutline" :size="20" />
        </button>
      </div>
    </div>
    <div class="content">
      <div class="notif-list">
        <div v-for="item in notifications" :key="item.id" class="notif-card">
          <img :src="item.avatar" class="avatar" alt="" />
          <div class="info">
            <div class="title-line">
              <span class="name">{{ item.name }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">{{ item.message }}</div>
          </div>
          <div class="actions-right">
            <n-button round size="small" type="default" class="send-msg-btn">
              {{ item.actionBtn }}
            </n-button>
          </div>
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
  gap: 16px;
}

.action-btn {
  background: none;
  border: none;
  padding: 4px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.notif-list {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.notif-card {
  display: flex;
  align-items: center;
  background: var(--lx-bg-card);
  padding: 20px;
  border-radius: var(--lx-radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  gap: 16px;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: #f0f0f0;
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.title-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.name {
  font-size: 15px;
  font-weight: 500;
  color: var(--lx-accent);
}

.date {
  font-size: 13px;
  color: var(--lx-text-muted);
}

.message {
  font-size: 14px;
  color: var(--lx-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.actions-right {
  padding-left: 16px;
}

.send-msg-btn {
  padding: 0 20px;
}
</style>
