<script setup lang="ts">
// Vue 响应式 API、侦听器与生命周期
import { ref, watch, onUnmounted } from 'vue'
// Naive UI 图标组件
import { NIcon } from 'naive-ui'
// Ionicons5 通话相关图标
import {
  MicOutline,
  MicOffOutline,
  VideocamOutline,
  DesktopOutline,
  CallOutline
} from '@vicons/ionicons5'
// 通用头像组件
import Avatar from '../Avatar.vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'

// 聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 应用 Store 实例
const appStore = useAppStore()
// 语音通话弹窗是否打开
const { voiceCallOpen } = storeToRefs(chatModalsStore)
// 关闭语音通话、打开视频通话的方法
const { closeVoiceCall, openVideoCall } = chatModalsStore
// 当前会话信息
const { currentSession } = storeToRefs(appStore)

// 麦克风是否开启
const micOn = ref(true)
// 通话阶段：振铃中 / 已接通
const phase = ref<'ringing' | 'connected'>('ringing')
// 已接通通话时长（秒）
const seconds = ref(0)

// 振铃超时定时器
let ringTimer: ReturnType<typeof setTimeout> | null = null
// 通话计时定时器
let durationTimer: ReturnType<typeof setInterval> | null = null

// 清理所有通话相关定时器
function cleanupTimers() {
  if (ringTimer) clearTimeout(ringTimer)
  if (durationTimer) clearInterval(durationTimer)
  ringTimer = null
  durationTimer = null
}

// 监听弹窗开关：打开时模拟振铃后接通并开始计时
watch(voiceCallOpen, open => {
  cleanupTimers()
  if (open) {
    phase.value = 'ringing'
    seconds.value = 0
    ringTimer = setTimeout(() => {
      phase.value = 'connected'
      durationTimer = setInterval(() => {
        seconds.value++
      }, 1000)
    }, 1800)
  }
})

// 组件卸载时清理定时器
onUnmounted(cleanupTimers)

// 根据通话阶段生成状态栏文案
const statusText = () => {
  if (phase.value === 'ringing') return '等待对方接听...'
  const m = Math.floor(seconds.value / 60).toString().padStart(2, '0')
  const s = (seconds.value % 60).toString().padStart(2, '0')
  return `通话中 ${m}:${s}`
}

// 挂断并关闭语音通话
function hangUp() {
  closeVoiceCall()
}

// 切换到视频通话
function switchToVideo() {
  closeVoiceCall()
  openVideoCall()
}
</script>

<template>
  <!-- 语音通话全屏弹窗 -->
  <Teleport to="body">
    <div v-if="voiceCallOpen" class="call-root">
      <div class="call-window">
        <!-- 顶部通话状态 -->
        <div class="call-top">
          <span class="status">{{ statusText() }}</span>
        </div>
        <!-- 中间：对方头像与名称 -->
        <div class="call-center">
          <Avatar
            v-if="currentSession"
            :text="currentSession.avatarText"
            :color="currentSession.avatarColor"
            :image-url="currentSession.avatarUrl"
            :size="88"
          />
          <p class="peer">{{ currentSession?.name || '好友' }}</p>
        </div>
        <!-- 底部通话控制栏 -->
        <div class="call-controls">
          <button type="button" class="ctl" @click="micOn = !micOn">
            <n-icon :component="micOn ? MicOutline : MicOffOutline" :size="26" />
            <span>{{ micOn ? '关闭麦克风' : '开启麦克风' }}</span>
          </button>
          <button type="button" class="ctl" @click="switchToVideo">
            <n-icon :component="VideocamOutline" :size="26" />
            <span>开启视频</span>
          </button>
          <button type="button" class="ctl muted" title="屏幕共享（后续版本）">
            <n-icon :component="DesktopOutline" :size="26" />
            <span>屏幕共享</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="CallOutline" :size="28" />
            <span>挂断</span>
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.call-root {
  position: fixed;
  inset: 0;
  z-index: 2200;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-window {
  width: min(420px, 90vw);
  background: linear-gradient(180deg, #3a3a3a 0%, #2a2a2a 100%);
  border-radius: var(--lx-radius);
  padding: 24px 20px 28px;
  color: var(--lx-bg-card);
  box-shadow: 0 16px 48px var(--lx-bg-overlay);
}

.call-top {
  text-align: center;
  margin-bottom: 24px;
}

.status {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.85);
}

.call-center {
  text-align: center;
  margin-bottom: 32px;
}

.call-center :deep(.avatar) {
  margin: 0 auto 12px;
}

.peer {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.call-controls {
  display: flex;
  justify-content: space-around;
  align-items: flex-start;
  gap: 8px;
}

.ctl {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  cursor: pointer;
  max-width: 72px;
}

.ctl span {
  line-height: 1.2;
}

.ctl.muted {
  opacity: 0.55;
  cursor: default;
}

.ctl.hangup {
  color: var(--lx-bg-card);
}

.ctl.hangup :deep(svg) {
  background: #e34d59;
  border-radius: 50%;
  padding: 10px;
  width: 48px !important;
  height: 48px !important;
}
</style>
