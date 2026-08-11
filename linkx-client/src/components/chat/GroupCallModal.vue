<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊语音/视频电话弹窗：UI 对齐单聊 VoiceCallModal / VideoCallModal，
 * 底层仍用 conference（scene=call）做多人 mesh，与会议室完全分开。
 */
import { ref, watch, computed, nextTick, onUnmounted, type ComponentPublicInstance } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { Mic, MicOff, Videocam, VideocamOff, Call } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useConferenceStore } from '../../stores/conference'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'
import { generateDefaultAvatar } from '../../utils/defaultAvatar'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../../utils/mediaUrl'

const message = useMessage()
const { t } = useI18n()
const conferenceStore = useConferenceStore()
const appStore = useAppStore()
const groupMeta = useGroupMetaStore()

const {
  showGroupCallUi,
  showGroupVoiceUi,
  showGroupVideoUi,
  phase,
  title,
  type,
  participants,
  micOn,
  cameraOn,
  localStream,
  remoteStreams,
  invitePrompt,
  errorMessage,
  myUserId
} = storeToRefs(conferenceStore)

const invitePassword = ref('')
const needInvitePassword = ref(false)
const seconds = ref(0)
const localVideoRef = ref<HTMLVideoElement | null>(null)
let durationTimer: ReturnType<typeof setInterval> | null = null
const joinedAt = ref(0)

const displayMembers = computed(() => {
  const sid = conferenceStore.conversationId || ''
  const members = groupMeta.members[sid] || []
  return participants.value
    .filter(p => p.admitStatus == null || Number(p.admitStatus) !== 0)
    .map(p => {
      const uid = String(p.userId)
      const member = members.find(m => String(m.id) === uid)
      const isMe = uid === myUserId.value
      const displayName =
        (p.nickname && String(p.nickname).trim()) ||
        member?.name ||
        (isMe ? appStore.userProfile.nickname : '') ||
        t('moments.user')
      const avatarCandidate =
        (p.avatar && String(p.avatar)) ||
        member?.avatarUrl ||
        (isMe ? appStore.userProfile.avatar : '') ||
        ''
      const avatar = isDisplayableMediaUrl(avatarCandidate)
        ? normalizeMediaUrl(avatarCandidate)
        : generateDefaultAvatar(displayName || uid, 160)
      return { ...p, userId: uid, isMe, displayName, avatar }
    })
})

const callTitle = computed(() => {
  if (title.value && title.value !== '多人会议') return title.value
  return type.value === 'voice' ? t('conference.voiceCallTitle') : t('conference.videoCallTitle')
})

const statusText = computed(() => {
  if (phase.value === 'lobby') {
    return invitePrompt.value?.restore
      ? t('conference.restoreHint')
      : t('conference.inviteCallTitle')
  }
  if (phase.value === 'waiting') return t('conference.waitingHint')
  const others = displayMembers.value.filter(p => !p.isMe).length
  if (others === 0) return t('conference.waitingOthers')
  const m = Math.floor(seconds.value / 60)
    .toString()
    .padStart(2, '0')
  const s = (seconds.value % 60).toString().padStart(2, '0')
  return `${t('conference.callMemberCount', { n: displayMembers.value.length })} ${m}:${s}`
})

watch(
  () =>
    showGroupCallUi.value && (phase.value === 'in_room' || phase.value === 'waiting')
      ? conferenceStore.conversationId
      : null,
  id => {
    if (id) void groupMeta.fetchMembers(id)
  },
  { immediate: true }
)

function clearDuration() {
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
  seconds.value = 0
}

watch(
  () => phase.value,
  p => {
    clearDuration()
    if (p === 'in_room') {
      joinedAt.value = Date.now()
      durationTimer = setInterval(() => {
        seconds.value = Math.floor((Date.now() - joinedAt.value) / 1000)
      }, 1000)
    }
  }
)

watch(errorMessage, msg => {
  if (!msg) return
  // 仅展示通话相关提示；「会议已结束」等留给会议室
  if (
    msg === t('conference.endedOk') ||
    msg === t('conference.removedFromMeeting')
  ) {
    return
  }
  message.info(msg)
  conferenceStore.clearError()
})

onUnmounted(() => {
  clearDuration()
})

function isPasswordError(e: unknown): boolean {
  const err = e as {
    code?: number
    response?: { status?: number; data?: { code?: number; message?: string } }
    message?: string
  }
  const status = err.response?.status
  const code = err.code ?? err.response?.data?.code
  const msg = (err.response?.data?.message || err.message || '').toLowerCase()
  return (
    status === 403 ||
    code === 403 ||
    msg.includes('password') ||
    msg.includes('密码')
  )
}

async function acceptInvite() {
  const prompt = invitePrompt.value
  if (!prompt) return
  const pwd = invitePassword.value.trim()
  if (needInvitePassword.value && !pwd) {
    message.warning(t('conference.passwordRequired'))
    return
  }
  try {
    await conferenceStore.joinExisting(
      prompt.conferenceId,
      String(appStore.userProfile.userId || ''),
      pwd || undefined
    )
    needInvitePassword.value = false
    invitePassword.value = ''
  } catch (e) {
    if (!needInvitePassword.value && isPasswordError(e)) {
      needInvitePassword.value = true
      message.info(t('conference.needPassword'))
      return
    }
    if (needInvitePassword.value && isPasswordError(e)) {
      message.error(t('conference.wrongPassword'))
      return
    }
    message.error((e as Error).message || t('conference.joinFail'))
  }
}

async function hangUp() {
  // 群电话：挂断即离开；发起人挂断由 store.leave 结束整场并清顶栏
  await conferenceStore.leave()
  message.success(t('conference.leaveCallOk'))
}

function avatarText(name: string) {
  return name?.charAt(0) || '友'
}

function remoteStreamOf(userId: string): MediaStream | null {
  return remoteStreams.value[userId] || null
}

function hasLiveVideo(userId: string): boolean {
  const stream = remoteStreamOf(userId)
  return !!stream?.getVideoTracks().some(t => t.readyState === 'live')
}

function hasLocalLiveVideo(): boolean {
  const stream = localStream.value
  return !!stream?.getVideoTracks().some(t => t.readyState === 'live' && t.enabled)
}

function bindRemoteAudio(userId: string) {
  return (el: Element | ComponentPublicInstance | null) => {
    if (!(el instanceof HTMLAudioElement)) return
    const stream = remoteStreamOf(userId)
    if (el.srcObject !== stream) el.srcObject = stream
    if (stream) {
      el.muted = false
      void el.play().catch(() => {})
    }
  }
}

function bindRemoteVideo(userId: string) {
  return (el: Element | ComponentPublicInstance | null) => {
    if (!(el instanceof HTMLVideoElement)) return
    const stream = remoteStreamOf(userId)
    if (el.srcObject !== stream) el.srcObject = stream
    if (stream) void el.play().catch(() => {})
  }
}

function bindLocalVideo(el: Element | ComponentPublicInstance | null) {
  if (!(el instanceof HTMLVideoElement)) {
    localVideoRef.value = null
    return
  }
  localVideoRef.value = el
  const stream = localStream.value
  if (el.srcObject !== stream) el.srcObject = stream
  el.muted = true
  if (stream) void el.play().catch(() => {})
}

watch(
  localStream,
  async () => {
    await nextTick()
    bindLocalVideo(localVideoRef.value)
  },
  { immediate: true }
)

watch(showGroupVideoUi, async visible => {
  if (!visible) return
  await nextTick()
  bindLocalVideo(localVideoRef.value)
})

watch(cameraOn, async on => {
  if (!on || !showGroupVideoUi.value) return
  await nextTick()
  bindLocalVideo(localVideoRef.value)
})
</script>

<template>
  <Teleport to="body">
    <!-- 来电 / 重连确认（通话风格） -->
    <div v-if="showGroupCallUi && invitePrompt && phase === 'lobby'" class="call-root">
      <div class="call-window invite-window">
        <p class="status">
          {{ invitePrompt.restore ? t('conference.restoreTitle') : t('conference.inviteCallTitle') }}
        </p>
        <div class="call-center">
          <p class="peer">{{ invitePrompt.title || callTitle }}</p>
          <p v-if="invitePrompt.restore" class="hint">{{ t('conference.restoreHint') }}</p>
        </div>
        <label v-if="needInvitePassword" class="invite-pwd">
          <span>{{ t('conference.password') }}</span>
          <input
            v-model="invitePassword"
            type="password"
            :placeholder="t('conference.passwordPlaceholder')"
            autocomplete="current-password"
            @keyup.enter="acceptInvite"
          />
        </label>
        <div class="call-controls">
          <button type="button" class="ctl hangup" @click="conferenceStore.dismissInvite()">
            <n-icon :component="Call" :size="28" />
            <span>{{ invitePrompt.restore ? t('conference.restoreLater') : t('common.cancel') }}</span>
          </button>
          <button type="button" class="ctl accept" @click="acceptInvite">
            <n-icon :component="Call" :size="28" />
            <span>{{ invitePrompt.restore ? t('conference.rejoin') : t('conference.joinCall') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 群语音电话 -->
    <div v-else-if="showGroupVoiceUi && (phase === 'in_room' || phase === 'waiting')" class="call-root">
      <div class="call-window">
        <div class="call-top">
          <span class="status">{{ statusText }}</span>
        </div>
        <div class="call-center">
          <p class="peer">{{ callTitle }}</p>
          <div class="avatar-grid">
            <div v-for="p in displayMembers" :key="p.userId" class="avatar-cell">
              <Avatar
                :text="avatarText(p.displayName)"
                color="var(--lx-success-strong)"
                :image-url="p.avatar || undefined"
                :size="64"
              />
              <span class="name">{{ p.isMe ? t('conference.you') : p.displayName }}</span>
              <span class="mic-dot" :class="{ off: p.isMe ? !micOn : !!p.muted }">
                <n-icon :component="(p.isMe ? !micOn : !!p.muted) ? MicOff : Mic" :size="12" />
              </span>
              <audio v-if="!p.isMe" :ref="bindRemoteAudio(p.userId)" autoplay playsinline />
            </div>
          </div>
        </div>
        <div class="call-controls cols-2">
          <button type="button" class="ctl" :class="{ off: !micOn }" @click="conferenceStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="28" />
            <span>{{ micOn ? t('conference.mute') : t('conference.unmute') }}</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="Call" :size="28" />
            <span>{{ t('conference.hangUp') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 群视频电话 -->
    <div v-else-if="showGroupVideoUi && (phase === 'in_room' || phase === 'waiting')" class="call-root">
      <div class="call-window video-window">
        <p class="status">{{ statusText }} · {{ callTitle }}</p>
        <div class="video-grid" :class="`n-${Math.min(displayMembers.length, 4)}`">
          <div v-for="p in displayMembers" :key="p.userId" class="video-tile">
            <video
              v-if="p.isMe && hasLocalLiveVideo()"
              :ref="bindLocalVideo"
              class="tile-video local"
              autoplay
              muted
              playsinline
            />
            <video
              v-else-if="!p.isMe && hasLiveVideo(p.userId)"
              :ref="bindRemoteVideo(p.userId)"
              class="tile-video"
              autoplay
              playsinline
            />
            <div v-else class="tile-placeholder">
              <Avatar
                :text="avatarText(p.displayName)"
                color="var(--lx-success-strong)"
                :image-url="p.avatar || undefined"
                :size="56"
              />
            </div>
            <audio v-if="!p.isMe" :ref="bindRemoteAudio(p.userId)" autoplay playsinline />
            <div class="tile-label">
              <n-icon
                v-if="p.isMe ? !micOn : !!p.muted"
                :component="MicOff"
                :size="12"
              />
              <span>{{ p.isMe ? t('conference.you') : p.displayName }}</span>
            </div>
          </div>
        </div>
        <div class="call-controls cols-3">
          <button type="button" class="ctl" :class="{ off: !micOn }" @click="conferenceStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="26" />
            <span>{{ micOn ? t('conference.mute') : t('conference.unmute') }}</span>
          </button>
          <button
            type="button"
            class="ctl"
            :class="{ off: !cameraOn }"
            @click="conferenceStore.toggleCamera()"
          >
            <n-icon :component="cameraOn ? Videocam : VideocamOff" :size="26" />
            <span>{{ cameraOn ? t('conference.stopVideo') : t('conference.startVideo') }}</span>
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
  z-index: var(--lx-z-call);
  background: var(--lx-bg-overlay, rgba(0, 0, 0, 0.45));
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-window {
  width: min(440px, 92vw);
  background: var(--lx-call-gradient);
  border-radius: var(--lx-radius);
  padding: var(--lx-space-4xl) var(--lx-space-4xl) var(--lx-space-5xl-minus);
  color: var(--lx-text-on-accent);
  box-shadow: var(--lx-shadow-popover);
}

.video-window {
  width: min(560px, 94vw);
  padding: 0;
  overflow: hidden;
  background: var(--lx-conf-bg-deep);
}

.call-top {
  text-align: center;
  margin-bottom: var(--lx-space-3xl);
}

.status {
  margin: 0;
  padding: var(--lx-space-xl);
  text-align: center;
  font-size: var(--lx-font);
  color: rgba(255, 255, 255, 0.9);
}

.call-window:not(.video-window) .status {
  padding: 0;
  font-size: var(--lx-font-lg);
  color: rgba(255, 255, 255, 0.85);
}

.call-center {
  text-align: center;
  margin-bottom: var(--lx-space-4xl);
}

.peer {
  margin: 0 0 var(--lx-space-2xl);
  font-size: var(--lx-font-xl);
  font-weight: 500;
}

.hint {
  margin: 0;
  font-size: var(--lx-font-md);
  color: rgba(255, 255, 255, 0.55);
}

.avatar-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lx-space-2xl) var(--lx-space-3xl);
  max-height: 280px;
  overflow: auto;
}

.avatar-cell {
  position: relative;
  width: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space-sm);
}

.avatar-cell .name {
  font-size: var(--lx-font-sm);
  color: rgba(255, 255, 255, 0.85);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mic-dot {
  position: absolute;
  right: 4px;
  top: 44px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(7, 193, 96, 0.9);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mic-dot.off {
  background: rgba(250, 81, 81, 0.95);
}

.invite-pwd {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
  margin: 0 0 var(--lx-space-2xl);
  font-size: var(--lx-font-md);
  color: rgba(255, 255, 255, 0.75);
}

.invite-pwd input {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: var(--lx-radius-xs);
  padding: var(--lx-space) var(--lx-space-md);
  background: rgba(0, 0, 0, 0.25);
  color: var(--lx-text-on-accent);
}

.call-controls {
  display: grid;
  align-items: start;
  gap: var(--lx-space-lg);
  padding: 0 var(--lx-space-xs);
}

.video-window .call-controls {
  padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space-3xl);
  background: var(--lx-bg-panel-deep);
}

.cols-2 {
  grid-template-columns: repeat(2, 1fr);
}

.cols-3 {
  grid-template-columns: repeat(3, 1fr);
}

.invite-window .call-controls {
  grid-template-columns: repeat(2, 1fr);
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
}

.ctl.hangup :deep(.n-icon) {
  background: var(--lx-danger);
}

.ctl.accept :deep(.n-icon) {
  background: var(--lx-success-strong);
}

.ctl span {
  line-height: var(--lx-leading-tight);
  text-align: center;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.92);
}

.video-grid {
  display: grid;
  gap: var(--lx-space-xs);
  height: 340px;
  background: var(--lx-bg-card);
  padding: var(--lx-space-xs);
}

.video-grid.n-1 {
  grid-template-columns: 1fr;
}

.video-grid.n-2 {
  grid-template-columns: 1fr 1fr;
}

.video-grid.n-3,
.video-grid.n-4 {
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
}

.video-tile {
  position: relative;
  background: var(--lx-text-body);
  border-radius: var(--lx-radius-xs);
  overflow: hidden;
  min-height: 0;
}

.tile-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tile-video.local {
  transform: scaleX(-1);
}

.tile-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lx-video-surface-gradient);
}

.tile-label {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-on-accent);
  text-shadow: 0 1px 2px var(--lx-black);
}
</style>
