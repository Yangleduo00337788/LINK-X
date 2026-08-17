<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
/**
 * 语音通话弹窗：真实 WebRTC，等待对端接听后建立媒体连接。
 */
import { ref, watch, onUnmounted, computed, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { Mic, MicOff, Videocam, Call } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useCallStore } from '../../stores/call'
import { useI18n } from '../../i18n'

const message = useMessage()
const { t } = useI18n()
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
  if (phase.value === 'outgoing') return t('extra.waitPeerAnswer')
  if (phase.value === 'connecting') return t('extra.connectingCall')
  const m = Math.floor(seconds.value / 60)
    .toString()
    .padStart(2, '0')
  const s = (seconds.value % 60).toString().padStart(2, '0')
  return t('extra.inCallDuration', { time: `${m}:${s}` })
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

onUnmounted(() => {
  clearDuration()
  if (durationTimer) clearInterval(durationTimer)
  durationTimer = null
})

async function hangUp() {
  await callStore.hangup()
}

async function switchToVideo() {
  message.info(t('extra.switchToVideoHint'))
}

function avatarText(name: string) {
  return name?.charAt(0) || t('extra.friendChar')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showVoiceUi" class="call-root lx-call-skin">
      <audio ref="remoteAudioRef" autoplay playsinline />
      <div class="call-window">
        <div class="call-top">
          <span class="status">{{ statusText }}</span>
        </div>
        <div class="call-center">
          <Avatar
            :text="avatarText(peerName)"
            color="var(--lx-success-strong)"
            :image-url="peerAvatar || undefined"
            :size="88"
          />
          <p class="peer">{{ peerName || t('extra.friend') }}</p>
          <div class="state-badges">
            <span class="badge" :class="{ off: !micOn }">
              <n-icon :component="micOn ? Mic : MicOff" :size="14" />
              {{ micOn ? t('extra.micOnShort') : t('extra.micOffShort') }}
            </span>
          </div>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl" :class="{ off: !micOn }" @click="callStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="28" />
            <span>{{ micOn ? t('extra.muteMic') : t('extra.unmuteMic') }}</span>
          </button>
          <button type="button" class="ctl" @click="switchToVideo">
            <n-icon :component="Videocam" :size="28" />
            <span>{{ t('chat.videoCall') }}</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="Call" :size="28" />
            <span>{{ t('conference.hangUp') }}</span>
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
  z-index: var(--lx-z-dialog);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-window {
  width: min(420px, 90vw);
  background: var(--lx-call-gradient);
  border-radius: var(--lx-radius);
  padding: var(--lx-space-4xl) var(--lx-space-4xl) var(--lx-space-5xl-minus);
  color: var(--lx-text-on-accent);
  box-shadow: var(--lx-shadow-popover);
}

.call-top {
  text-align: center;
  margin-bottom: var(--lx-space-4xl);
}

.status {
  font-size: var(--lx-font-lg);
  color: rgba(255, 255, 255, 0.85);
}

.call-center {
  text-align: center;
  margin-bottom: var(--lx-space-5xl-minus);
}

.call-center :deep(.avatar) {
  margin: 0 auto var(--lx-space-lg);
}

.peer {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font-xl);
  font-weight: 500;
  color: var(--lx-text-on-accent);
}

.state-badges {
  display: flex;
  justify-content: center;
  gap: var(--lx-space);
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  padding: var(--lx-space-xs) var(--lx-space-md);
  border-radius: var(--lx-radius-pill);
  font-size: var(--lx-font-sm);
  background: rgba(7, 193, 96, 0.2);
  color: var(--lx-success-strong);
}

.badge.off {
  background: rgba(250, 81, 81, 0.2);
  color: var(--lx-danger);
}

.call-controls {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  align-items: start;
  gap: var(--lx-space-lg);
  padding: 0 var(--lx-space-xs);
}

.ctl {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space);
  border: none;
  background: transparent;
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-xs);
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
  color: var(--lx-text-on-accent);
}

.ctl.off :deep(.n-icon) {
  background: rgba(250, 81, 81, 0.85);
  color: var(--lx-text-on-accent);
}

.ctl.hangup :deep(.n-icon) {
  background: var(--lx-danger);
  color: var(--lx-text-on-accent);
}

.ctl span {
  line-height: var(--lx-leading-tight);
  text-align: center;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.92);
}
</style>
