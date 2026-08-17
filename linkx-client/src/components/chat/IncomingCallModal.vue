<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 来电弹窗：语音 / 视频来电，QQ 风格全屏弹出。
 */
import { ref, watch, onMounted, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { CallOutline, CloseOutline, Videocam } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useCallStore } from '../../stores/call'
import { useAppStore } from '../../stores/app'
import { useI18n } from '../../i18n'

const message = useMessage()
const { t } = useI18n()
const callStore = useCallStore()
const appStore = useAppStore()
const {
  showIncomingUi,
  peerName,
  peerAvatar,
  callType,
  errorMessage,
  localStream,
  cameraOn
} = storeToRefs(callStore)
const { userProfile } = storeToRefs(appStore)

const localVideoRef = ref<HTMLVideoElement | null>(null)

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    callStore.clearError()
  }
})

watch(showIncomingUi, visible => {
  if (visible) {
    void nextTick(() => bindLocalPreview())
  }
})

watch(localStream, () => {
  void bindLocalPreview()
})

onMounted(() => {
  void bindLocalPreview()
})

async function bindLocalPreview() {
  if (callType.value !== 'video') return
  await nextTick()
  const el = localVideoRef.value
  const stream = localStream.value
  if (!el) return
  if (el.srcObject !== stream) el.srcObject = stream
  if (stream) {
    el.muted = true
    try {
      await el.play()
    } catch {
      /* ignore */
    }
  }
}

async function accept() {
  try {
    await callStore.acceptIncoming()
  } catch (e) {
    message.error((e as Error).message || t('extra.acceptFail'))
  }
}

async function acceptVoiceOnly() {
  callStore.cameraOn = false
  localStream.value?.getVideoTracks().forEach(track => {
    track.enabled = false
  })
  await accept()
}

async function reject() {
  await callStore.rejectIncoming()
}

function avatarText(name: string) {
  return name?.charAt(0) || t('extra.friendChar')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showIncomingUi" class="incoming-root lx-call-skin">
      <div class="incoming-window" :class="{ video: callType === 'video' }">
        <template v-if="callType === 'video'">
          <div class="video-stage">
            <video
              v-show="localStream && cameraOn"
              ref="localVideoRef"
              class="video-preview"
              autoplay
              muted
              playsinline
            />
            <div v-if="!localStream || !cameraOn" class="video-fallback" />
            <div class="video-head">
              <Avatar
                :text="avatarText(peerName)"
                color="var(--lx-success-strong)"
                :image-url="peerAvatar || undefined"
                :size="40"
              />
              <div class="video-head-text">
                <p class="peer">{{ peerName || t('extra.friend') }}</p>
                <p class="hint">{{ t('extra.inviteVideoCall') }}</p>
              </div>
            </div>
            <div class="video-self-label">
              {{ userProfile.nickname || t('chat.me') }}
            </div>
          </div>
          <div class="incoming-controls video-controls">
            <button type="button" class="ctl" @click="acceptVoiceOnly">
              <span class="ctl-icon voice-only">
                <n-icon :component="CallOutline" :size="22" />
              </span>
              <span>{{ t('extra.acceptVoiceOnly') }}</span>
            </button>
            <button type="button" class="ctl" @click="accept">
              <span class="ctl-icon accept">
                <n-icon :component="Videocam" :size="22" />
              </span>
              <span>{{ t('extra.accept') }}</span>
            </button>
            <button type="button" class="ctl" @click="reject">
              <span class="ctl-icon reject">
                <n-icon :component="CloseOutline" :size="22" />
              </span>
              <span>{{ t('conference.hangUp') }}</span>
            </button>
          </div>
        </template>

        <template v-else>
          <div class="voice-body">
            <Avatar
              :text="avatarText(peerName)"
              color="var(--lx-success-strong)"
              :image-url="peerAvatar || undefined"
              :size="96"
            />
            <p class="peer">{{ peerName || t('extra.friend') }}</p>
            <p class="hint">{{ t('extra.inviteVoiceCall') }}</p>
          </div>
          <div class="incoming-controls">
            <button type="button" class="ctl" @click="reject">
              <span class="ctl-icon reject">
                <n-icon :component="CloseOutline" :size="26" />
              </span>
              <span>{{ t('conference.hangUp') }}</span>
            </button>
            <button type="button" class="ctl" @click="accept">
              <span class="ctl-icon accept">
                <n-icon :component="CallOutline" :size="26" />
              </span>
              <span>{{ t('extra.accept') }}</span>
            </button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.incoming-root {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-dialog-call-in);
  background: rgba(10, 12, 18, 0.72);
  backdrop-filter: blur(18px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.incoming-window {
  width: min(400px, 92vw);
  border-radius: 16px;
  overflow: hidden;
  background: rgba(22, 24, 30, 0.92);
  color: #fff;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.45);
}

.incoming-window.video {
  width: min(520px, 94vw);
}

.video-stage {
  position: relative;
  height: 300px;
  background: #111;
}

.video-preview,
.video-fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-fallback {
  background: linear-gradient(145deg, #2a2f3a, #151820);
}

.video-head {
  position: absolute;
  top: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.45);
}

.video-head-text .peer {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.video-head-text .hint {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}

.video-self-label {
  position: absolute;
  left: 12px;
  bottom: 12px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.35);
  padding: 4px 8px;
  border-radius: 8px;
}

.voice-body {
  padding: 48px 24px 28px;
  text-align: center;
}

.voice-body :deep(.avatar) {
  margin: 0 auto 16px;
}

.peer {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.hint {
  margin: 8px 0 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.68);
}

.incoming-controls {
  display: flex;
  justify-content: center;
  gap: 36px;
  padding: 22px 16px 28px;
}

.video-controls {
  gap: 28px;
}

.ctl {
  border: none;
  background: transparent;
  color: #fff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-width: 72px;
}

.ctl-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ctl-icon.accept {
  background: #22c55e;
}

.ctl-icon.reject {
  background: #ef4444;
}

.ctl-icon.voice-only {
  background: rgba(255, 255, 255, 0.16);
}
</style>
