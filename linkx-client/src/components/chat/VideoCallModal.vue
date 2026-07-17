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
  VideocamOffOutline,
  DesktopOutline,
  GridOutline,
  CallOutline
} from '@vicons/ionicons5'
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
// 视频通话弹窗是否打开
const { videoCallOpen } = storeToRefs(chatModalsStore)
// 关闭视频通话弹窗的方法
const { closeVideoCall } = chatModalsStore
// 用户资料与当前会话
const { userProfile, currentSession } = storeToRefs(appStore)

// 麦克风是否开启
const micOn = ref(true)
// 摄像头是否开启
const videoOn = ref(true)
// 通话阶段：振铃中 / 已接通
const phase = ref<'ringing' | 'connected'>('ringing')
// 本地视频元素引用
const localVideoRef = ref<HTMLVideoElement | null>(null)
// 是否成功获取本地视频流
const hasLocalVideo = ref(false)
// 本地媒体流对象
let mediaStream: MediaStream | null = null
// 振铃超时定时器
let ringTimer: ReturnType<typeof setTimeout> | null = null

// 请求摄像头与麦克风权限并绑定到 video 元素
async function startCamera() {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    hasLocalVideo.value = true
    if (localVideoRef.value) {
      localVideoRef.value.srcObject = mediaStream
    }
  } catch {
    /* 无摄像头时保留占位 UI */
  }
}

// 停止所有媒体轨道并释放资源
function stopCamera() {
  mediaStream?.getTracks().forEach(t => t.stop())
  mediaStream = null
  hasLocalVideo.value = false
  if (localVideoRef.value) {
    localVideoRef.value.srcObject = null
  }
}

// 监听弹窗开关：打开时模拟振铃后接通，关闭时清理摄像头
watch(videoCallOpen, open => {
  if (ringTimer) clearTimeout(ringTimer)
  if (open) {
    phase.value = 'ringing'
    ringTimer = setTimeout(async () => {
      phase.value = 'connected'
      if (videoOn.value) await startCamera()
    }, 1500)
  } else {
    stopCamera()
    phase.value = 'ringing'
  }
})

// 监听摄像头开关：接通后动态启停视频流
watch(videoOn, async on => {
  if (!videoCallOpen.value || phase.value !== 'connected') return
  if (on) await startCamera()
  else stopCamera()
})

// 组件卸载时清理定时器与摄像头
onUnmounted(() => {
  if (ringTimer) clearTimeout(ringTimer)
  stopCamera()
})

// 挂断并关闭视频通话
function hangUp() {
  closeVideoCall()
}

// 根据通话阶段生成状态栏文案
const statusText = () =>
  phase.value === 'ringing'
    ? `正在呼叫 ${currentSession.value?.name || '好友'}…`
    : `与 ${currentSession.value?.name || '好友'} 视频通话中`
</script>

<template>
  <!-- 视频通话全屏弹窗 -->
  <Teleport to="body">
    <div v-if="videoCallOpen" class="call-root">
      <div class="call-window">
        <!-- 通话状态文案 -->
        <p class="status">{{ statusText() }}</p>
        <!-- 视频画面区域 -->
        <div class="video-stage">
          <video
            v-if="phase === 'connected' && videoOn && hasLocalVideo"
            ref="localVideoRef"
            class="local-video"
            autoplay
            muted
            playsinline
          />
          <div v-else class="video-placeholder">
            <span class="ph-text">{{ phase === 'ringing' ? '等待接听' : '摄像头已关闭' }}</span>
          </div>
          <div class="pip">
            <span class="pip-name">{{ userProfile.nickname }}</span>
          </div>
        </div>
        <!-- 底部通话控制栏 -->
        <div class="call-controls">
          <button type="button" class="ctl" @click="micOn = !micOn">
            <n-icon :component="micOn ? MicOutline : MicOffOutline" :size="24" />
            <span>{{ micOn ? '关闭麦克风' : '开启麦克风' }}</span>
          </button>
          <button type="button" class="ctl" @click="videoOn = !videoOn">
            <n-icon :component="videoOn ? VideocamOutline : VideocamOffOutline" :size="24" />
            <span>{{ videoOn ? '关闭视频' : '开启视频' }}</span>
          </button>
          <button type="button" class="ctl dim" title="后续版本">
            <n-icon :component="DesktopOutline" :size="24" />
            <span>屏幕共享</span>
          </button>
          <button type="button" class="ctl dim" title="后续版本">
            <n-icon :component="GridOutline" :size="24" />
            <span>宫格模式</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="CallOutline" :size="26" />
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
  width: min(480px, 92vw);
  background: #1e1e1e;
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: 0 16px 48px var(--lx-bg-overlay);
}

.status {
  margin: 0;
  padding: 14px;
  text-align: center;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.3);
}

.video-stage {
  position: relative;
  height: 320px;
  background: #2c2c2c;
}

.local-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.video-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #3d3d3d, #252525);
}

.ph-text {
  color: var(--lx-text-secondary);
  font-size: 13px;
}

.pip {
  position: absolute;
  left: 12px;
  bottom: 12px;
  width: 100px;
  height: 72px;
  background: #444;
  border-radius: var(--lx-radius);
  border: 2px solid rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: flex-end;
  padding: 4px 6px;
}

.pip-name {
  font-size: 10px;
  color: var(--lx-bg-card);
  text-shadow: 0 1px 2px #000;
}

.call-controls {
  display: flex;
  justify-content: space-around;
  padding: 16px 8px 20px;
  background: #2a2a2a;
}

.ctl {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: var(--lx-bg-card);
  font-size: 10px;
  cursor: pointer;
  max-width: 64px;
}

.ctl.dim {
  opacity: 0.5;
  cursor: default;
}

.ctl.hangup :deep(svg) {
  background: var(--lx-danger);
  border-radius: 50%;
  padding: 8px;
}
</style>
