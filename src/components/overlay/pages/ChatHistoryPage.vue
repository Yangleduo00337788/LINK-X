<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChatbubblesOutline, TimeOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import EmptyState from '../../common/EmptyState.vue'

const appStore = useAppStore()
const { currentSession, currentMessages } = storeToRefs(appStore)

const historyMessages = computed(() =>
  currentMessages.value.filter(m => m.type !== 'system')
)

function historyPreview(msg: (typeof currentMessages.value)[number]) {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  if (msg.type === 'voice') return '[语音]'
  if (msg.type === 'redPacket') return `[红包] ${msg.redPacketGreeting || msg.content}`
  return msg.content
}
</script>

<template>
  <div class="page-wrap history-page">
    <section class="panel-card history-card">
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
