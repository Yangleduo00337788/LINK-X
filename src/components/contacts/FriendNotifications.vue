<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { FilterOutline, TrashOutline } from '@vicons/ionicons5'

const notifications = [
  {
    id: 1,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zling',
    name: 'Z铃ღ',
    action: '请求加为好友',
    date: '2026/06/07',
    message: '我是来自群聊“cursor分享群”的____Z铃ღ',
    source: 'QQ群-cursor分享群',
    status: '已同意'
  },
  {
    id: 2,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=ooo',
    name: 'o',
    action: '正在验证你的邀请',
    date: '2025/07/21',
    message: '请求添加对方为好友',
    source: '',
    status: '等待验证'
  },
  {
    id: 3,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=gg',
    name: '哥哥你干嘛',
    action: '正在验证你的邀请',
    date: '2025/02/24',
    message: '请求添加对方为好友',
    source: '',
    status: '等待验证'
  },
  {
    id: 4,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=yh',
    name: 'yh',
    action: '请求加为好友',
    date: '2025/01/29',
    message: '我是yh',
    source: 'QQ号查找',
    status: '已同意'
  },
  {
    id: 5,
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zwz',
    name: '吱唔猪',
    action: '请求加为好友',
    date: '2024/07/18',
    message: '我是',
    source: 'QQ号查找',
    status: '已同意'
  }
]
</script>

<template>
  <div class="notifications-view">
    <div class="header">
      <h2 class="title">好友通知</h2>
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
              <span class="action-text">{{ item.action }}</span>
              <span class="date">{{ item.date }}</span>
            </div>
            <div class="message">留言: {{ item.message }}</div>
            <div v-if="item.source" class="source">来源: {{ item.source }}</div>
          </div>
          <div class="status" :class="{ 'waiting': item.status === '等待验证' }">
            {{ item.status }}
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
  background: var(--lx-bg-window, #f5f5f5);
}

.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--lx-divider, #ebebeb);
}

.title {
  font-size: 18px;
  font-weight: 500;
  color: #333;
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
  color: #666;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #333;
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
  align-items: flex-start;
  background: #ffffff;
  padding: 20px;
  border-radius: 8px;
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
}

.title-line {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
}

.name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  color: #12b7f5;
}

.action-text {
  font-size: 14px;
  color: #333;
}

.date {
  font-size: 13px;
  color: #999;
}

.message, .source {
  font-size: 13px;
  color: #666;
}

.status {
  font-size: 14px;
  color: #999;
  padding-left: 16px;
  white-space: nowrap;
}

.status.waiting {
  color: #ff9800;
}
</style>
