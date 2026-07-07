<script setup lang="ts">
import { ref, computed } from 'vue'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'

const search = ref('')

const moments = ref([
  {
    id: '1',
    name: '晚香玉',
    avatarText: '晚',
    avatarColor: '#12b7f5',
    time: '2小时前',
    text: '今天天气不错，适合写代码 ☁️',
    likes: 12,
    comments: 3
  },
  {
    id: '2',
    name: 'LinkX 团队',
    avatarText: 'L',
    avatarColor: '#9a9a9a',
    time: '昨天',
    text: 'LinkX 桌面端 UI 持续打磨中，欢迎反馈。',
    likes: 28,
    comments: 8
  },
  {
    id: '3',
    name: '开发小伙伴',
    avatarText: '开',
    avatarColor: '#f56c6c',
    time: '3天前',
    text: '朋友圈功能演示：动态、点赞与评论待对接后端。',
    likes: 5,
    comments: 1
  }
])

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return moments.value
  return moments.value.filter(
    m => m.name.toLowerCase().includes(q) || m.text.toLowerCase().includes(q)
  )
})
</script>

<template>
  <div class="moments-panel">
    <PanelSearchBar v-model="search" placeholder="搜索朋友圈" />
    <div class="list">
      <article v-for="m in filtered" :key="m.id" class="moment-card">
        <div class="moment-head">
          <Avatar :text="m.avatarText" :color="m.avatarColor" :size="40" />
          <div class="meta">
            <span class="name">{{ m.name }}</span>
            <span class="time">{{ m.time }}</span>
          </div>
        </div>
        <p class="text">{{ m.text }}</p>
        <div class="foot">
          <span>{{ m.likes }} 赞</span>
          <span>{{ m.comments }} 评论</span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.moments-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel, #f3f3f3);
  display: flex;
  flex-direction: column;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 12px;
}

.moment-card {
  background: rgba(255, 255, 255, 0.65);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 8px;
  box-shadow: var(--lx-shadow-soft, 0 1px 2px rgba(0, 0, 0, 0.04));
}

.moment-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.time {
  font-size: 11px;
  color: #999;
}

.text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #444;
}

.foot {
  margin-top: 10px;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #888;
}
</style>