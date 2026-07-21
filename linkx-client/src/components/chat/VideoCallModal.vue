<script setup lang="ts">
/**
 * 视频通话弹窗：真实 WebRTC，本地预览 + 远端画面。
 */
import { ref, watch, computed, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { Mic, MicOff, Videocam, VideocamOff, Call } from '@vicons/ionicons5'
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

async function bindVideo(
  el: HTMLVideoElement | null,
  stream: MediaStream | null,
  opts?: { muted?: boolean }
) {
  await nextTick()
  if (!el) return
  if (el.srcObject !== stream) {
    el.srcObject = stream
  }
  if (stream) {
    el.muted = opts?.muted ?? false
    el.volume = 1
    try {
      await el.play()
    } catch {
      /* ignore */
    }
  }
}

watch(
  localStream,
  stream => {
    void bindVideo(localVideoRef.value, stream, { muted: true })
  },
  { immediate: true }
)

watch(
  remoteStream,
  stream => {
    void bindVideo(remoteVideoRef.value, stream, { muted: false })
  },
  { immediate: true }
)

watch(showVideoUi, async visible => {
  if (!visible) return
  await nextTick()
  void bindVideo(localVideoRef.value, localStream.value, { muted: true })
  void bindVideo(remoteVideoRef.value, remoteStream.value, { muted: false })
})

watch(phase, p => {
  if (p === 'connected') {
    void bindVideo(remoteVideoRef.value, remoteStream.value, { muted: false })
  }
})

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
          <div class="state-badges">
            <span class="badge" :class="{ off: !micOn }" :title="micOn ? '麦克风已开' : '麦克风已关'">
              <n-icon :component="micOn ? Mic : MicOff" :size="16" />
            </span>
            <span class="badge" :class="{ off: !cameraOn }" :title="cameraOn ? '摄像头已开' : '摄像头已关'">
              <n-icon :component="cameraOn ? Videocam : VideocamOff" :size="16" />
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
            <span v-if="!localStream" class="pip-name">正在打开摄像头…</span>
            <span v-else-if="!cameraOn" class="pip-name">摄像头已关</span>
            <span v-else class="pip-name">{{ userProfile.nickname }}</span>
          </div>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl" :class="{ off: !micOn }" @click="callStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="26" />
            <span>{{ micOn ? '关闭麦克风' : '开启麦克风' }}</span>
          </button>
          <button type="button" class="ctl" :class="{ off: !cameraOn }" @click="callStore.toggleCamera()">
            <n-icon :component="cameraOn ? Videocam : VideocamOff" :size="26" />
            <span>{{ cameraOn ? '关闭视频' : '开启视频' }}</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="Call" :size="26" />
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
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
}

.state-badges {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 8px;
  z-index: 2;
}

.badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(7, 193, 96, 0.9);
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
}

.badge.off {
  background: rgba(250, 81, 81, 0.95);
}

.pip {
  position: absolute;
  left: 12px;
  bottom: 12px;
  width: 112px;
  height: 80px;
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
  color: #fff;
  text-shadow: 0 1px 2px #000;
  padding: 4px 6px;
}

.call-controls {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  align-items: start;
  gap: 12px;
  padding: 16px 16px 20px;
  background: #2a2a2a;
}

.ctl {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: #fff;
  font-size: 11px;
  cursor: pointer;
  min-width: 0;
  padding: 0;
}

.ctl :deep(.n-icon) {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.ctl.off :deep(.n-icon) {
  background: rgba(250, 81, 81, 0.85);
  color: #fff;
}

.ctl.hangup :deep(.n-icon) {
  background: #fa5151;
  color: #fff;
}

.ctl span {
  line-height: 1.2;
  text-align: center;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.92);
}
</style>
