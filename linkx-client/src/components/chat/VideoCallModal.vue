<script setup lang="ts">
/**
 * 视频通话弹窗：真实 WebRTC，本地预览 + 远端画面。
 */
import { ref, watch, computed, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  MicOutline,
  MicOffOutline,
  VideocamOutline,
  VideocamOffOutline,
  CallOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useCallStore } from '../../stores/call'
import { useAppStore } from '../../stores/app'

const message = useMessage()
const callStore = useCallStore()
const appStore = useAppStore()
const {
  showVideoUi,
  phase,
  peerName,
  micOn,
  cameraOn,
  errorMessage,
  localStream,
  remoteStream
} = storeToRefs(callStore)
const { userProfile } = storeToRefs(appStore)

const localVideoRef = ref<HTMLVideoElement | null>(null)
const remoteVideoRef = ref<HTMLVideoElement | null>(null)

const statusText = computed(() => {
  if (phase.value === 'outgoing') return `正在呼叫 ${peerName.value || '好友'}…`
  if (phase.value === 'connecting') return '正在接通…'
  return `与 ${peerName.value || '好友'} 视频通话中`
})

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    callStore.clearError()
  }
})

watch(
  localStream,
  async stream => {
    await nextTick()
    const el = localVideoRef.value
    if (el) {
      el.srcObject = stream
      if (stream) void el.play().catch(() => {})
    }
  },
  { immediate: true }
)

watch(
  remoteStream,
  async stream => {
    await nextTick()
    const el = remoteVideoRef.value
    if (el) {
      el.srcObject = stream
      if (stream) void el.play().catch(() => {})
    }
  },
  { immediate: true }
)

async function hangUp() {
  await callStore.hangup()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showVideoUi" class="call-root">
      <div class="call-window">
        <p class="status">{{ statusText }}</p>
        <div class="video-stage">
          <video
            v-show="remoteStream"
            ref="remoteVideoRef"
            class="remote-video"
            autoplay
            playsinline
          />
          <div v-if="!remoteStream" class="video-placeholder">
            <span class="ph-text">
              {{ phase === 'outgoing' ? '等待接听' : phase === 'connecting' ? '正在接通…' : '等待对方画面' }}
            </span>
          </div>
          <div class="pip">
            <video
              v-show="localStream && cameraOn"
              ref="localVideoRef"
              class="local-video"
              autoplay
              muted
              playsinline
            />
            <span v-if="!cameraOn" class="pip-name">摄像头已关</span>
            <span v-else class="pip-name">{{ userProfile.nickname }}</span>
          </div>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl" @click="callStore.toggleMic()">
            <n-icon :component="micOn ? MicOutline : MicOffOutline" :size="24" />
            <span>{{ micOn ? '关闭麦克风' : '开启麦克风' }}</span>
          </button>
          <button type="button" class="ctl" @click="callStore.toggleCamera()">
            <n-icon :component="cameraOn ? VideocamOutline : VideocamOffOutline" :size="24" />
            <span>{{ cameraOn ? '关闭视频' : '开启视频' }}</span>
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

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  padding: 0;
}

.local-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.pip-name {
  position: relative;
  z-index: 1;
  font-size: 10px;
  color: var(--lx-bg-card);
  text-shadow: 0 1px 2px #000;
  padding: 4px 6px;
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

.ctl.hangup :deep(svg) {
  background: var(--lx-danger);
  border-radius: 50%;
  padding: 8px;
}
</style>
