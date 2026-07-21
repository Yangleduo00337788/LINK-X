<script setup lang="ts">
/**
 * 语音通话弹窗：真实 WebRTC，等待对端接听后建立媒体连接。
 */
import { ref, watch, onUnmounted, computed, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { Mic, MicOff, Videocam, Call } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useCallStore } from '../../stores/call'

const message = useMessage()
const callStore = useCallStore()
const {
  showVoiceUi,
  phase,
  peerName,
  peerAvatar,
  micOn,
  errorMessage,
  connectedAt,
  remoteStream
} = storeToRefs(callStore)

const seconds = ref(0)
let durationTimer: ReturnType<typeof setInterval> | null = null
const remoteAudioRef = ref<HTMLAudioElement | null>(null)

const statusText = computed(() => {
  if (phase.value === 'outgoing') return '等待对方接听...'
  if (phase.value === 'connecting') return '正在接通...'
  const m = Math.floor(seconds.value / 60)
    .toString()
    .padStart(2, '0')
  const s = (seconds.value % 60).toString().padStart(2, '0')
  return `通话中 ${m}:${s}`
})

function clearDuration() {
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
  seconds.value = 0
}

async function bindRemoteAudio(stream: MediaStream | null) {
  await nextTick()
  const el = remoteAudioRef.value
  if (!el) return
  if (el.srcObject !== stream) {
    el.srcObject = stream
  }
  if (stream) {
    el.muted = false
    el.volume = 1
    try {
      await el.play()
    } catch {
      /* ignore */
    }
  }
}

watch(phase, p => {
  clearDuration()
  if (p === 'connected') {
    const base = connectedAt.value || Date.now()
    durationTimer = setInterval(() => {
      seconds.value = Math.floor((Date.now() - base) / 1000)
    }, 1000)
    void bindRemoteAudio(remoteStream.value)
  }
})

watch(remoteStream, stream => {
  void bindRemoteAudio(stream)
})

watch(showVoiceUi, async visible => {
  if (!visible) return
  await nextTick()
  void bindRemoteAudio(remoteStream.value)
})

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    callStore.clearError()
  }
})

onUnmounted(clearDuration)

async function hangUp() {
  await callStore.hangup()
}

async function switchToVideo() {
  message.info('请挂断后重新发起视频通话')
}

function avatarText(name: string) {
  return name?.charAt(0) || '友'
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showVoiceUi" class="call-root">
      <audio ref="remoteAudioRef" autoplay playsinline />
      <div class="call-window">
        <div class="call-top">
          <span class="status">{{ statusText }}</span>
        </div>
        <div class="call-center">
          <Avatar
            :text="avatarText(peerName)"
            color="#07c160"
            :image-url="peerAvatar || undefined"
            :size="88"
          />
          <p class="peer">{{ peerName || '好友' }}</p>
          <div class="state-badges">
            <span class="badge" :class="{ off: !micOn }">
              <n-icon :component="micOn ? Mic : MicOff" :size="14" />
              {{ micOn ? '麦克风开' : '麦克风关' }}
            </span>
          </div>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl" :class="{ off: !micOn }" @click="callStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="28" />
            <span>{{ micOn ? '关闭麦克风' : '开启麦克风' }}</span>
          </button>
          <button type="button" class="ctl" @click="switchToVideo">
            <n-icon :component="Videocam" :size="28" />
            <span>视频通话</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="Call" :size="28" />
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
  padding: 24px 24px 28px;
  color: #fff;
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
  margin-bottom: 28px;
}

.call-center :deep(.avatar) {
  margin: 0 auto 12px;
}

.peer {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
}

.state-badges {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(7, 193, 96, 0.2);
  color: #07c160;
}

.badge.off {
  background: rgba(250, 81, 81, 0.2);
  color: #fa5151;
}

.call-controls {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  align-items: start;
  gap: 12px;
  padding: 0 4px;
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
