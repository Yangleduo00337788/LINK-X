<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { NIcon } from 'naive-ui'
import {
  MicOutline,
  MicOffOutline,
  VideocamOutline,
  VideocamOffOutline,
  DesktopOutline,
  GridOutline,
  CallOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { videoCallOpen } = storeToRefs(chatModalsStore)
const { closeVideoCall } = chatModalsStore
const { userProfile, currentSession } = storeToRefs(appStore)

const micOn = ref(true)
const videoOn = ref(true)
const phase = ref<'ringing' | 'connected'>('ringing')
const localVideoRef = ref<HTMLVideoElement | null>(null)
const hasLocalVideo = ref(false)
let mediaStream: MediaStream | null = null
let ringTimer: ReturnType<typeof setTimeout> | null = null

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

function stopCamera() {
  mediaStream?.getTracks().forEach(t => t.stop())
  mediaStream = null
  hasLocalVideo.value = false
  if (localVideoRef.value) {
    localVideoRef.value.srcObject = null
  }
}

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

watch(videoOn, async on => {
  if (!videoCallOpen.value || phase.value !== 'connected') return
  if (on) await startCamera()
  else stopCamera()
})

onUnmounted(() => {
  if (ringTimer) clearTimeout(ringTimer)
  stopCamera()
})

function hangUp() {
  closeVideoCall()
}

const statusText = () =>
  phase.value === 'ringing'
    ? `正在呼叫 ${currentSession.value?.name || '好友'}…`
    : `与 ${currentSession.value?.name || '好友'} 视频通话中`
</script>

<template>
  <Teleport to="body">
    <div v-if="videoCallOpen" class="call-root">
      <div class="call-window">
        <p class="status">{{ statusText() }}</p>
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
  background: #e34d59;
  border-radius: 50%;
  padding: 8px;
}
</style>
