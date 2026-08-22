<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
/**
 * 视频通话弹窗：真实 WebRTC，本地预览 + 远端画面。
 */
import { ref, watch, computed, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { Mic, MicOff, Videocam, VideocamOff, Call } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useCallStore } from '../../stores/call'
import { useAppStore } from '../../stores/app'
import { useI18n } from '../../i18n'
import ModalOverlayCaption from '../ModalOverlayCaption.vue'

const message = useMessage()
const { t } = useI18n()
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
  const peer = peerName.value || t('extra.friend')
  if (phase.value === 'outgoing') return t('extra.callingPeer', { name: peer })
  if (phase.value === 'connecting') return t('extra.connectingCall')
  return t('extra.videoWithPeer', { name: peer })
})

const placeholderText = computed(() => {
  if (phase.value === 'outgoing') return t('extra.waitAnswer')
  if (phase.value === 'connecting') return t('extra.connectingCall')
  return t('extra.waitingRemoteVideo')
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
    <div v-if="showVideoUi" class="call-root lx-call-skin">
      <ModalOverlayCaption />
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
            <span class="ph-text">{{ placeholderText }}</span>
          </div>
          <div class="state-badges">
            <span
              class="badge"
              :class="{ off: !micOn }"
              :title="micOn ? t('extra.micOnTitle') : t('extra.micOffTitle')"
            >
              <n-icon :component="micOn ? Mic : MicOff" :size="16" />
            </span>
            <span
              class="badge"
              :class="{ off: !cameraOn }"
              :title="cameraOn ? t('extra.cameraOnTitle') : t('extra.cameraOffTitle')"
            >
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
            <span v-if="!localStream" class="pip-name">{{ t('extra.openingCamera') }}</span>
            <span v-else-if="!cameraOn" class="pip-name">{{ t('extra.cameraOffLabel') }}</span>
            <span v-else class="pip-name">{{ userProfile.nickname }}</span>
          </div>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl" :class="{ off: !micOn }" @click="callStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="26" />
            <span>{{ micOn ? t('extra.muteMic') : t('extra.unmuteMic') }}</span>
          </button>
          <button type="button" class="ctl" :class="{ off: !cameraOn }" @click="callStore.toggleCamera()">
            <n-icon :component="cameraOn ? Videocam : VideocamOff" :size="26" />
            <span>{{ cameraOn ? t('extra.muteVideo') : t('extra.unmuteVideo') }}</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="Call" :size="26" />
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
  width: min(480px, 92vw);
  background: var(--lx-conf-bg-deep);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: var(--lx-shadow-popover);
}

.status {
  margin: 0;
  padding: var(--lx-space-xl);
  text-align: center;
  font-size: var(--lx-font);
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.3);
}

.video-stage {
  position: relative;
  height: 320px;
  background: var(--lx-conf-bg-void);
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
  background: var(--lx-video-surface-gradient);
}

.ph-text {
  color: rgba(255, 255, 255, 0.55);
  font-size: var(--lx-font-md);
}

.state-badges {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: var(--lx-space);
  z-index: var(--lx-z-raised-2);
}

.badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(7, 193, 96, 0.9);
  color: var(--lx-text-on-accent);
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
  background: var(--lx-call-control-bg);
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
  z-index: var(--lx-z-raised);
  font-size: var(--lx-font-2xs);
  color: var(--lx-text-on-accent);
  text-shadow: 0 1px 2px var(--lx-black);
  padding: var(--lx-space-xs) var(--lx-space-sm);
}

.call-controls {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  align-items: start;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space-3xl);
  background: var(--lx-conf-panel);
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
