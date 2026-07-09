<script setup lang="ts">
// Vue 计算属性
import { computed } from 'vue'
// Naive UI 图标组件
import { NIcon } from 'naive-ui'
// Ionicons5 聊天与时间图标
import { ChatbubblesOutline, TimeOutline } from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../../../stores/app'
// 空状态组件
import EmptyState from '../../common/EmptyState.vue'

// 应用 Store 实例
const appStore = useAppStore()
// 当前会话与消息列表
const { currentSession, currentMessages } = storeToRefs(appStore)

// 过滤掉系统消息后的聊天记录
const historyMessages = computed(() =>
  currentMessages.value.filter(m => m.type !== 'system')
)

// 根据消息类型生成列表预览文案
function historyPreview(msg: (typeof currentMessages.value)[number]) {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  if (msg.type === 'voice') return '[语音]'
  if (msg.type === 'redPacket') return `[红包] ${msg.redPacketGreeting || msg.content}`
  return msg.content
}
</script>

<template>
  <!-- 聊天记录浏览页面 -->
  <div class="page-wrap history-page">
    <!-- 会话信息与消息列表 -->
    <section class="panel-card history-card">
      <!-- 会话头部：名称与消息总数 -->
      <div class="history-hero">
        <div class="history-avatar">
          <n-icon :component="ChatbubblesOutline" :size="28" />
        </div>
        <div class="history-meta">
          <h2 class="history-name">{{ currentSession?.name || '—' }}</h2>
          <p class="history-sub">
            <n-icon :component="TimeOutline" :size="14" />
            共 {{ historyMessages.length }} 条消息
          </p>
        </div>
      </div>

      <!-- 消息滚动列表或空状态 -->
      <div v-if="historyMessages.length" class="history-scroll">
        <div
          v-for="msg in historyMessages"
          :key="msg.id"
          class="history-item"
          :class="{ self: msg.isSelf }"
        >
          <div class="history-bubble">
            <p class="history-text">{{ historyPreview(msg) }}</p>
            <span class="history-time">{{ msg.time }}</span>
          </div>
        </div>
      </div>
      <EmptyState
        v-else
        title="暂无消息"
        description="当前会话还没有聊天记录"
      />
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
