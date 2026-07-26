<script setup lang="ts">
/**
 * 多人会议房（演讲者视图）：主画面优先说话人，其他人右上角小窗；本人显示本地摄像头。
 */
import { ref, watch, computed, nextTick, onUnmounted } from 'vue'
import { NIcon, NPopover, useMessage } from 'naive-ui'
import {
  Mic,
  MicOff,
  Videocam,
  VideocamOff,
  Call,
  PeopleOutline,
  ChevronUpOutline,
  CloseOutline,
  CheckmarkOutline,
  DesktopOutline,
  HandLeftOutline,
  ChatbubblesOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useConferenceStore } from '../../stores/conference'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'
import { generateDefaultAvatar } from '../../utils/defaultAvatar'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../../utils/mediaUrl'
import type { ChatMessage } from '../../types'

const message = useMessage()
const { t } = useI18n()
const conferenceStore = useConferenceStore()
const appStore = useAppStore()
const groupMeta = useGroupMetaStore()
const {
  visible,
  phase,
  title,
  participants,
  micOn,
  cameraOn,
  type,
  localStream,
  remoteStreams,
  networkHint,
  invitePrompt,
  errorMessage,
  isHost,
  isHostOrCoHost,
  audioInputs,
  videoInputs,
  selectedAudioId,
  selectedVideoId,
  myUserId,
  activeSpeakerId,
  handRaised,
  screenSharing,
  chatOpen,
  raisedHands
} = storeToRefs(conferenceStore)

const localVideoRef = ref<HTMLVideoElement | null>(null)
const joinedAt = ref(0)
const elapsedSec = ref(0)
const membersPanelOpen = ref(false)
const invitePassword = ref('')
const needInvitePassword = ref(false)
const chatDraft = ref('')
const chatListRef = ref<HTMLElement | null>(null)
let elapsedTimer: ReturnType<typeof setInterval> | null = null

const displayParticipants = computed(() => {
  const sid = conferenceStore.conversationId || ''
  const members = groupMeta.members[sid] || []
  return participants.value.map(p => {
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
    const role = p.role || ''
    return {
      ...p,
      userId: uid,
      isMe,
      isHostRole: role === 'host' || uid === String(conferenceStore.creatorId),
      isCoHostRole: role === 'co-host',
      waitingAdmit: p.admitStatus != null && Number(p.admitStatus) === 0,
      handRaised: !!raisedHands.value[uid],
      displayName,
      avatar
    }
  })
})

const inRoomMembers = computed(() => displayParticipants.value.filter(p => !p.waitingAdmit))
const waitingMembers = computed(() => displayParticipants.value.filter(p => p.waitingAdmit))

watch(
  () =>
    phase.value === 'in_room' || phase.value === 'waiting'
      ? conferenceStore.conversationId
      : null,
  id => {
    if (id) void groupMeta.fetchMembers(id)
  },
  { immediate: true }
)

/** 主画面：优先有实时视频的人，其次 activeSpeaker，再主持人 */
const speaker = computed(() => {
  const list = inRoomMembers.value
  const withVideo = list.find(p => {
    if (p.isMe) return showLocalVideo(p)
    return hasLiveRemoteVideo(p.userId)
  })
  if (withVideo) return withVideo
  const activeId = activeSpeakerId.value
  if (activeId) {
    const found = list.find(p => p.userId === activeId)
    if (found) return found
  }
  return list.find(p => p.isHostRole) || list.find(p => p.isMe) || list[0] || null
})

const pipParticipants = computed(() => {
  const mainId = speaker.value?.userId
  return inRoomMembers.value.filter(p => p.userId !== mainId)
})

const chatMessages = computed(() => {
  const cid = conferenceStore.conversationId
  if (!cid) return [] as ChatMessage[]
  const list = appStore.messagesBySession[cid] || []
  return list.filter(m => !m.type || m.type === 'text').slice(-80)
})

watch(chatMessages, async () => {
  if (!chatOpen.value) return
  await nextTick()
  const el = chatListRef.value
  if (el) el.scrollTop = el.scrollHeight
})

const elapsedLabel = computed(() => {
  const s = elapsedSec.value
  const hh = Math.floor(s / 3600)
  const mm = Math.floor((s % 3600) / 60)
  const ss = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return hh > 0 ? `${pad(hh)}:${pad(mm)}:${pad(ss)}` : `${pad(mm)}:${pad(ss)}`
})

const audioOptions = computed(() =>
  audioInputs.value.map(d => ({
    label: d.label || t('conference.defaultMic'),
    value: d.deviceId
  }))
)
const videoOptions = computed(() =>
  videoInputs.value.map(d => ({
    label: d.label || t('conference.defaultCam'),
    value: d.deviceId
  }))
)

function participantLabel(p: { isMe: boolean; displayName: string }) {
  return p.isMe ? `${p.displayName}（${t('conference.you')}）` : p.displayName
}

function remoteStreamOf(userId: string): MediaStream | null {
  return remoteStreams.value[userId] || null
}

function hasLiveRemoteVideo(userId: string, _videoOff?: boolean): boolean {
  const stream = remoteStreamOf(userId)
  if (!stream) return false
  // 以真实媒体轨为准：只要有 live 视频轨就显示，避免过期 videoOff 把画面挡住
  return stream.getVideoTracks().some(t => t.readyState === 'live')
}

function isMuted(p: { isMe: boolean; muted?: boolean }) {
  return p.isMe ? !micOn.value : !!p.muted
}

function isVideoOff(p: { isMe: boolean; userId?: string; videoOff?: boolean }) {
  if (p.isMe) return !cameraOn.value
  // 已有远端视频时不显示「关摄像头」图标
  if (p.userId && hasLiveRemoteVideo(p.userId)) return false
  return !!p.videoOff
}

function showLocalVideo(p: { isMe: boolean; videoOff?: boolean }) {
  if (!p.isMe) return false
  if (screenSharing.value) return true
  return type.value === 'video' && cameraOn.value && !p.videoOff
}

function showRemoteVideo(p: { isMe: boolean; userId: string; videoOff?: boolean }) {
  return !p.isMe && hasLiveRemoteVideo(p.userId, p.videoOff)
}

function showRemoteAudioOnly(p: { isMe: boolean; userId: string; videoOff?: boolean }) {
  return !p.isMe && !!remoteStreamOf(p.userId) && !showRemoteVideo(p)
}

async function attachLocalVideo(el: HTMLVideoElement | null) {
  localVideoRef.value = el
  if (!el) return
  const stream = localStream.value
  if (!stream) return
  if (el.srcObject !== stream) el.srcObject = stream
  el.muted = true
  try {
    await el.play()
  } catch {
    /* ignore */
  }
}

function bindLocalVideo(el: unknown) {
  const video = (el as HTMLVideoElement | null) || null
  void attachLocalVideo(video)
}

const remoteVideoEls = new Map<string, HTMLVideoElement>()
const remoteAudioEls = new Map<string, HTMLAudioElement>()

async function attachRemoteMedia(userId: string) {
  const stream = remoteStreamOf(userId)
  const video = remoteVideoEls.get(userId)
  const audio = remoteAudioEls.get(userId)
  if (video && stream) {
    if (video.srcObject !== stream) video.srcObject = stream
    try {
      await video.play()
    } catch {
      /* ignore */
    }
  }
  if (audio && stream && !video) {
    if (audio.srcObject !== stream) audio.srcObject = stream
    try {
      await audio.play()
    } catch {
      /* ignore */
    }
  }
}

function bindRemoteVideo(userId: string) {
  return (el: unknown) => {
    const video = (el as HTMLVideoElement | null) || null
    if (!video) {
      remoteVideoEls.delete(userId)
      return
    }
    remoteVideoEls.set(userId, video)
    void attachRemoteMedia(userId)
  }
}

function bindRemoteAudio(userId: string) {
  return (el: unknown) => {
    const audio = (el as HTMLAudioElement | null) || null
    if (!audio) {
      remoteAudioEls.delete(userId)
      return
    }
    remoteAudioEls.set(userId, audio)
    void attachRemoteMedia(userId)
  }
}

function onAvatarError(e: Event, name: string) {
  const img = e.target as HTMLImageElement
  if (!img || img.dataset.fallback === '1') return
  img.dataset.fallback = '1'
  img.src = generateDefaultAvatar(name || '用户', 160)
}

watch(
  remoteStreams,
  () => {
    for (const userId of remoteVideoEls.keys()) {
      void attachRemoteMedia(userId)
    }
    for (const userId of remoteAudioEls.keys()) {
      void attachRemoteMedia(userId)
    }
  },
  { deep: true }
)

watch(localStream, () => {
  void attachLocalVideo(localVideoRef.value)
})

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    conferenceStore.clearError()
  }
})

watch(
  () => invitePrompt.value,
  prompt => {
    invitePassword.value = ''
    needInvitePassword.value = !!prompt?.hasPassword
  }
)

watch(
  () => phase.value === 'in_room' && visible.value,
  inRoom => {
    if (!inRoom) membersPanelOpen.value = false
    if (elapsedTimer) {
      clearInterval(elapsedTimer)
      elapsedTimer = null
    }
    if (inRoom) {
      joinedAt.value = Date.now()
      elapsedSec.value = 0
      elapsedTimer = setInterval(() => {
        elapsedSec.value = Math.floor((Date.now() - joinedAt.value) / 1000)
      }, 1000)
    }
  },
  { immediate: true }
)

watch(
  [
    localStream,
    cameraOn,
    screenSharing,
    () => speaker.value?.userId,
    () => pipParticipants.value.map(p => p.userId).join(',')
  ],
  async () => {
    await nextTick()
    await attachLocalVideo(localVideoRef.value)
  }
)

watch(
  remoteStreams,
  async () => {
    await nextTick()
    for (const id of Object.keys(remoteStreams.value)) {
      await attachRemoteMedia(id)
    }
  },
  { deep: true }
)

onUnmounted(() => {
  if (elapsedTimer) clearInterval(elapsedTimer)
  remoteVideoEls.clear()
  remoteAudioEls.clear()
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

async function leave() {
  await conferenceStore.leave()
  message.success(t('conference.leftOk'))
}

async function endMeeting() {
  try {
    await conferenceStore.endAsHost()
    message.success(t('conference.endedOk'))
  } catch (e) {
    message.error((e as Error).message || t('conference.endFail'))
  }
}

function minimizeRoom() {
  conferenceStore.minimizeUi()
}

function toggleChat() {
  conferenceStore.chatOpen = !conferenceStore.chatOpen
  if (conferenceStore.chatOpen) membersPanelOpen.value = false
}

function openMembers() {
  membersPanelOpen.value = !membersPanelOpen.value
  if (membersPanelOpen.value) conferenceStore.chatOpen = false
}

async function sendChatText() {
  const text = chatDraft.value.trim()
  const cid = conferenceStore.conversationId
  if (!text || !cid) return
  chatDraft.value = ''
  const prev = appStore.currentSessionId
  appStore.currentSessionId = cid
  try {
    await appStore.sendMessage(text)
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  } finally {
    appStore.currentSessionId = prev
  }
}

async function onToggleRaise() {
  try {
    await conferenceStore.toggleRaise()
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  }
}

async function onToggleScreenShare() {
  try {
    await conferenceStore.toggleScreenShare()
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  }
}

async function onTransferHost(userId: string) {
  try {
    await conferenceStore.transferHostTo(userId)
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  }
}

async function onSetCoHost(userId: string, enable: boolean) {
  try {
    await conferenceStore.setCoHost(userId, enable)
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  }
}

async function onAdmit(userId: string) {
  try {
    await conferenceStore.admitUser(userId)
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  }
}
</script>

<template>
  <Teleport to="body">
    <!-- 被邀请：轻量确认层 -->
    <div v-if="invitePrompt && phase === 'lobby'" class="invite-mask">
      <div class="invite-card">
        <h3>{{ invitePrompt.restore ? t('conference.restoreTitle') : t('conference.inviteTitle') }}</h3>
        <p>{{ invitePrompt.title }}</p>
        <p v-if="invitePrompt.restore" class="invite-sub">{{ t('conference.restoreHint') }}</p>
        <label v-if="needInvitePassword" class="invite-pwd">
          <span>{{ t('conference.password') }}</span>
          <input
            v-model="invitePassword"
            type="password"
            class="invite-pwd-input"
            :placeholder="t('conference.passwordPlaceholder')"
            autocomplete="current-password"
            @keyup.enter="acceptInvite"
          />
        </label>
        <div class="invite-actions">
          <button type="button" class="btn ghost" @click="conferenceStore.dismissInvite()">
            {{ invitePrompt.restore ? t('conference.restoreLater') : t('common.cancel') }}
          </button>
          <button type="button" class="btn primary" @click="acceptInvite">
            {{ invitePrompt.restore ? t('conference.rejoin') : t('conference.join') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 等候室 -->
    <div v-if="visible && phase === 'waiting'" class="waiting-root">
      <div class="waiting-card">
        <h3>{{ t('conference.waitingTitle') }}</h3>
        <p>{{ title }}</p>
        <p class="waiting-hint">{{ t('conference.waitingHint') }}</p>
        <button type="button" class="btn primary" @click="leave">
          {{ t('conference.leave') }}
        </button>
      </div>
    </div>

    <div
      v-if="visible && phase === 'in_room'"
      class="room-root"
      :class="{
        'room-root--members-open': membersPanelOpen,
        'room-root--chat-open': chatOpen
      }"
    >
      <!-- 顶栏：会议名 / 人数 / 时长 -->
      <header class="room-header">
        <div class="header-left">
          <div class="title-block">
            <h2>{{ title }}</h2>
            <div class="meta-row">
              <span>{{ t('conference.memberCount', { n: inRoomMembers.length }) }}</span>
              <span class="dot">·</span>
              <span class="timer">{{ elapsedLabel }}</span>
              <span v-if="networkHint" class="hint">{{ networkHint }}</span>
            </div>
          </div>
        </div>
        <button type="button" class="header-close" :title="t('conference.minimize')" @click="minimizeRoom">
          <n-icon :component="CloseOutline" :size="20" />
        </button>
      </header>

      <!-- 演讲者视图：说话人/主持人全屏，其他人右上角小窗 -->
      <div class="stage">
        <div v-if="speaker" class="speaker-stage">
          <div class="main-tile" :class="{ me: speaker.isMe }">
            <video
              v-if="showLocalVideo(speaker)"
              :ref="bindLocalVideo"
              class="tile-video"
              :class="{ 'tile-video--local': !screenSharing }"
              autoplay
              playsinline
              muted
            />
            <video
              v-else-if="showRemoteVideo(speaker)"
              :ref="bindRemoteVideo(speaker.userId)"
              class="tile-video"
              autoplay
              playsinline
            />
            <div v-else class="tile-avatar-wrap">
              <img
                :src="speaker.avatar"
                class="tile-avatar tile-avatar--lg"
                alt=""
                @error="onAvatarError($event, speaker.displayName || speaker.userId)"
              />
            </div>
            <audio
              v-if="showRemoteAudioOnly(speaker)"
              :ref="bindRemoteAudio(speaker.userId)"
              autoplay
            />
            <div class="tile-footer">
              <div class="tile-name">
                <n-icon v-if="isMuted(speaker)" class="mic-off" :component="MicOff" :size="14" />
                <n-icon
                  v-if="isVideoOff(speaker) && !screenSharing"
                  class="cam-off"
                  :component="VideocamOff"
                  :size="14"
                />
                <span>{{ participantLabel(speaker) }}</span>
                <span v-if="speaker.isHostRole" class="host-badge">{{ t('conference.host') }}</span>
                <span v-else-if="speaker.isCoHostRole" class="host-badge cohost">
                  {{ t('conference.coHost') }}
                </span>
              </div>
            </div>
          </div>

          <div v-if="pipParticipants.length" class="pip-strip">
            <div
              v-for="p in pipParticipants"
              :key="p.userId"
              class="pip-tile"
              :class="{ me: p.isMe, muted: isMuted(p), 'video-off': isVideoOff(p) }"
            >
              <video
                v-if="showLocalVideo(p)"
                :ref="bindLocalVideo"
                class="tile-video"
                :class="{ 'tile-video--local': !screenSharing }"
                autoplay
                playsinline
                muted
              />
              <video
                v-else-if="showRemoteVideo(p)"
                :ref="bindRemoteVideo(p.userId)"
                class="tile-video"
                autoplay
                playsinline
              />
              <div v-else class="tile-avatar-wrap">
                <img
                  :src="p.avatar"
                  class="tile-avatar"
                  alt=""
                  @error="onAvatarError($event, p.displayName || p.userId)"
                />
              </div>
              <audio
                v-if="showRemoteAudioOnly(p)"
                :ref="bindRemoteAudio(p.userId)"
                autoplay
              />
              <div class="tile-footer pip-footer">
                <div class="tile-name">
                  <n-icon v-if="isMuted(p)" class="mic-off" :component="MicOff" :size="12" />
                  <n-icon v-if="isVideoOff(p)" class="cam-off" :component="VideocamOff" :size="12" />
                  <n-icon v-if="p.handRaised" class="hand-on" :component="HandLeftOutline" :size="12" />
                  <span>{{ participantLabel(p) }}</span>
                </div>
                <div v-if="isHostOrCoHost && !p.isMe" class="tile-host-actions">
                  <button type="button" @click="conferenceStore.muteTarget(p.userId, !p.muted)">
                    {{ p.muted ? t('conference.unmute') : t('conference.mute') }}
                  </button>
                  <button
                    type="button"
                    class="danger"
                    @click="conferenceStore.removeTarget(p.userId)"
                  >
                    {{ t('conference.remove') }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底栏 -->
      <footer class="toolbar">
        <div class="toolbar-inner">
          <div class="tool-item-wrap">
            <button
              type="button"
              class="tool-btn"
              :class="{ off: !micOn }"
              @click="conferenceStore.toggleMic()"
            >
              <span class="tool-icon">
                <n-icon :component="micOn ? Mic : MicOff" :size="22" />
              </span>
              <span class="tool-label">{{ micOn ? t('conference.mute') : t('conference.unmute') }}</span>
            </button>
            <n-popover
              v-if="audioOptions.length"
              trigger="click"
              placement="top"
              :show-arrow="false"
              raw
            >
              <template #trigger>
                <button type="button" class="tool-caret" :title="t('conference.mic')">
                  <n-icon :component="ChevronUpOutline" :size="12" />
                </button>
              </template>
              <div class="device-menu">
                <div class="device-menu-title">{{ t('conference.mic') }}</div>
                <button
                  v-for="opt in audioOptions"
                  :key="opt.value"
                  type="button"
                  class="device-item"
                  :class="{ active: opt.value === selectedAudioId }"
                  @click="conferenceStore.switchAudioDevice(opt.value)"
                >
                  <n-icon
                    v-if="opt.value === selectedAudioId"
                    :component="CheckmarkOutline"
                    :size="14"
                  />
                  <span v-else class="check-spacer" />
                  <span class="device-label">{{ opt.label }}</span>
                </button>
              </div>
            </n-popover>
          </div>

          <div v-if="type === 'video'" class="tool-item-wrap">
            <button
              type="button"
              class="tool-btn"
              :class="{ off: !cameraOn }"
              :disabled="screenSharing"
              @click="conferenceStore.toggleCamera()"
            >
              <span class="tool-icon">
                <n-icon :component="cameraOn ? Videocam : VideocamOff" :size="22" />
              </span>
              <span class="tool-label">
                {{ cameraOn ? t('conference.stopVideo') : t('conference.startVideo') }}
              </span>
            </button>
            <n-popover
              v-if="videoOptions.length && !screenSharing"
              trigger="click"
              placement="top"
              :show-arrow="false"
              raw
            >
              <template #trigger>
                <button type="button" class="tool-caret" :title="t('conference.camera')">
                  <n-icon :component="ChevronUpOutline" :size="12" />
                </button>
              </template>
              <div class="device-menu">
                <div class="device-menu-title">{{ t('conference.camera') }}</div>
                <button
                  v-for="opt in videoOptions"
                  :key="opt.value"
                  type="button"
                  class="device-item"
                  :class="{ active: opt.value === selectedVideoId }"
                  @click="conferenceStore.switchVideoDevice(opt.value)"
                >
                  <n-icon
                    v-if="opt.value === selectedVideoId"
                    :component="CheckmarkOutline"
                    :size="14"
                  />
                  <span v-else class="check-spacer" />
                  <span class="device-label">{{ opt.label }}</span>
                </button>
              </div>
            </n-popover>
          </div>

          <button
            type="button"
            class="tool-btn"
            :class="{ active: screenSharing }"
            @click="onToggleScreenShare"
          >
            <span class="tool-icon">
              <n-icon :component="DesktopOutline" :size="22" />
            </span>
            <span class="tool-label">
              {{ screenSharing ? t('conference.stopShare') : t('conference.screenShare') }}
            </span>
          </button>

          <button
            type="button"
            class="tool-btn"
            :class="{ active: handRaised }"
            @click="onToggleRaise"
          >
            <span class="tool-icon">
              <n-icon :component="HandLeftOutline" :size="22" />
            </span>
            <span class="tool-label">
              {{ handRaised ? t('conference.lowerHand') : t('conference.raiseHand') }}
            </span>
          </button>

          <button
            type="button"
            class="tool-btn"
            :class="{ active: chatOpen }"
            @click="toggleChat"
          >
            <span class="tool-icon">
              <n-icon :component="ChatbubblesOutline" :size="22" />
            </span>
            <span class="tool-label">{{ t('conference.chat') }}</span>
          </button>

          <button
            type="button"
            class="tool-btn"
            :class="{ active: membersPanelOpen }"
            @click="openMembers"
          >
            <span class="tool-icon">
              <n-icon :component="PeopleOutline" :size="22" />
            </span>
            <span class="tool-label">
              {{ t('conference.members') }} ({{ inRoomMembers.length }})
            </span>
          </button>

          <div class="tool-divider" />

          <button type="button" class="tool-btn hang" @click="leave">
            <span class="tool-icon hang-icon">
              <n-icon :component="Call" :size="22" />
            </span>
            <span class="tool-label">{{ t('conference.leave') }}</span>
          </button>

          <button v-if="isHost" type="button" class="tool-btn end" @click="endMeeting">
            <span class="tool-icon end-icon">
              <n-icon :component="CloseOutline" :size="20" />
            </span>
            <span class="tool-label">{{ t('conference.end') }}</span>
          </button>
        </div>
      </footer>

      <!-- 成员列表面板 -->
      <aside v-if="membersPanelOpen" class="members-panel">
        <div class="members-panel-head">
          <h3>{{ t('conference.members') }} ({{ displayParticipants.length }})</h3>
          <button type="button" class="members-close" @click="membersPanelOpen = false">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </div>
        <ul class="members-list">
          <li v-for="p in inRoomMembers" :key="p.userId" class="member-row">
            <img :src="p.avatar" class="member-av" alt="" />
            <div class="member-meta">
              <div class="member-name">
                <span>{{ participantLabel(p) }}</span>
                <span v-if="p.isHostRole" class="host-badge">{{ t('conference.host') }}</span>
                <span v-else-if="p.isCoHostRole" class="host-badge cohost">
                  {{ t('conference.coHost') }}
                </span>
                <span v-if="p.handRaised" class="raised-badge">{{ t('conference.raisedBadge') }}</span>
              </div>
              <div class="member-status">
                <n-icon
                  :component="isMuted(p) ? MicOff : Mic"
                  :size="14"
                  :class="{ off: isMuted(p) }"
                />
                <n-icon
                  :component="isVideoOff(p) ? VideocamOff : Videocam"
                  :size="14"
                  :class="{ off: isVideoOff(p) }"
                />
              </div>
            </div>
            <div v-if="!p.isMe" class="member-actions">
              <template v-if="isHostOrCoHost">
                <button type="button" @click="conferenceStore.muteTarget(p.userId, !p.muted)">
                  {{ p.muted ? t('conference.unmute') : t('conference.mute') }}
                </button>
                <button type="button" class="danger" @click="conferenceStore.removeTarget(p.userId)">
                  {{ t('conference.remove') }}
                </button>
              </template>
              <template v-if="isHost && !p.isHostRole">
                <button type="button" @click="onTransferHost(p.userId)">
                  {{ t('conference.transferHost') }}
                </button>
                <button type="button" @click="onSetCoHost(p.userId, !p.isCoHostRole)">
                  {{ p.isCoHostRole ? t('conference.unsetCoHost') : t('conference.setCoHost') }}
                </button>
              </template>
            </div>
          </li>
          <li v-if="waitingMembers.length" class="waiting-sep">{{ t('conference.waitingBadge') }}</li>
          <li v-for="p in waitingMembers" :key="'w-' + p.userId" class="member-row waiting">
            <img :src="p.avatar" class="member-av" alt="" />
            <div class="member-meta">
              <div class="member-name">
                <span>{{ participantLabel(p) }}</span>
                <span class="waiting-badge">{{ t('conference.waitingBadge') }}</span>
              </div>
            </div>
            <div v-if="isHostOrCoHost" class="member-actions">
              <button type="button" class="admit" @click="onAdmit(p.userId)">
                {{ t('conference.admit') }}
              </button>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 右侧聊天侧栏 -->
      <aside v-if="chatOpen" class="chat-panel-side">
        <div class="members-panel-head">
          <h3>{{ t('conference.chat') }}</h3>
          <button type="button" class="members-close" @click="conferenceStore.chatOpen = false">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </div>
        <div ref="chatListRef" class="chat-list">
          <p v-if="!chatMessages.length" class="chat-empty">{{ t('conference.chatEmpty') }}</p>
          <div v-for="m in chatMessages" :key="m.id" class="chat-row" :class="{ self: m.isSelf }">
            <div class="chat-sender">{{ m.isSelf ? t('conference.you') : m.senderName || '' }}</div>
            <div class="chat-bubble">{{ m.content }}</div>
          </div>
        </div>
        <div class="chat-compose">
          <input
            v-model="chatDraft"
            type="text"
            class="chat-input"
            :placeholder="t('conference.chatPlaceholder')"
            @keyup.enter="sendChatText"
          />
          <button type="button" class="btn primary chat-send" @click="sendChatText">
            {{ t('conference.chatSend') }}
          </button>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.invite-mask,
.waiting-root {
  position: fixed;
  inset: 0;
  z-index: 12000;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}
.invite-card,
.waiting-card {
  width: min(360px, 90vw);
  background: #2b2b2b;
  border-radius: 12px;
  padding: 22px;
  color: #f0f0f0;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
  text-align: center;
}
.invite-card {
  text-align: left;
}
.invite-card h3,
.waiting-card h3 {
  margin: 0 0 8px;
  font-size: 17px;
}
.invite-card p,
.waiting-card p {
  margin: 0 0 18px;
  color: rgba(255, 255, 255, 0.65);
}
.invite-card .invite-sub,
.waiting-hint {
  margin: -10px 0 18px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
}
.invite-pwd {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0 0 16px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}
.invite-pwd-input {
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: #1f1f1f;
  color: #f0f0f0;
  padding: 9px 12px;
  font-size: 14px;
  outline: none;
}
.invite-pwd-input:focus {
  border-color: #006eff;
}
.invite-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.btn {
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
}
.btn.ghost {
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
}
.btn.primary {
  background: #006eff;
  color: #fff;
}

.room-root {
  position: fixed;
  inset: 0;
  z-index: 11900;
  background: #1a1a1a;
  color: #f5f5f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.room-header {
  flex-shrink: 0;
  height: 52px;
  padding: 0 16px 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(0, 0, 0, 0.35);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.header-left {
  min-width: 0;
}
.title-block h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}
.meta-row .dot {
  opacity: 0.5;
}
.meta-row .timer {
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.7);
}
.hint {
  margin-left: 8px;
  color: #ffb454;
}
.header-close {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.header-close:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.stage {
  flex: 1;
  min-height: 0;
  position: relative;
  padding: 0;
  overflow: hidden;
}
.speaker-stage {
  position: absolute;
  inset: 0;
}
.main-tile {
  position: absolute;
  inset: 0;
  background: #111;
  overflow: hidden;
}
.main-tile.me {
  box-shadow: inset 0 0 0 2px #006eff;
}
.pip-strip {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100% - 24px);
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
  transition: right 0.2s ease;
}
.room-root--members-open .pip-strip,
.room-root--chat-open .pip-strip {
  right: min(332px, calc(88vw + 12px));
}
.pip-tile {
  position: relative;
  width: 168px;
  aspect-ratio: 16 / 10;
  background: #2a2a2a;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.45);
  flex-shrink: 0;
}
.pip-tile.me {
  border-color: #006eff;
}
.tile-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #111;
}
.tile-video--local {
  transform: scaleX(-1);
}
.tile-avatar-wrap {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #2e2e2e 0%, #1a1a1a 100%);
}
.tile-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
}
.tile-avatar--lg {
  width: min(140px, 22vw);
  height: min(140px, 22vw);
}
.tile-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.75));
}
.pip-footer {
  padding: 6px 8px;
}
.tile-name {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}
.pip-footer .tile-name {
  font-size: 12px;
}
.tile-name span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mic-off,
.cam-off,
.hand-on {
  color: #ff6b6b;
  flex-shrink: 0;
}
.hand-on {
  color: #ffb454;
}
.host-badge {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 3px;
  background: #006eff;
  color: #fff;
}
.host-badge.cohost {
  background: #5b8def;
}
.raised-badge,
.waiting-badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(255, 180, 84, 0.25);
  color: #ffb454;
}
.tile-host-actions {
  display: none;
  gap: 4px;
  flex-shrink: 0;
}
.pip-tile:hover .tile-host-actions {
  display: flex;
}
.tile-host-actions button {
  border: none;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 11px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
.tile-host-actions .danger {
  background: rgba(229, 72, 77, 0.85);
}

.toolbar {
  flex-shrink: 0;
  padding: 10px 16px 18px;
  background: #111;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.toolbar-inner {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}
.tool-item-wrap {
  position: relative;
  display: flex;
  align-items: flex-end;
}
.tool-btn {
  min-width: 72px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 10px 4px;
  border-radius: 8px;
}
.tool-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.06);
}
.tool-btn.active .tool-icon {
  background: rgba(0, 110, 255, 0.35);
}
.tool-btn:disabled {
  opacity: 0.55;
  cursor: default;
}
.tool-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease;
}
.tool-btn.off .tool-icon {
  background: #e5484d;
  color: #fff;
}
.tool-btn.hang .hang-icon {
  background: #e5484d;
  color: #fff;
  border-radius: 50%;
}
.tool-btn.hang .hang-icon :deep(svg) {
  transform: rotate(135deg);
}
.tool-btn.end .end-icon {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12px;
}
.tool-label {
  font-size: 12px;
  line-height: 1.2;
  color: rgba(255, 255, 255, 0.75);
  white-space: nowrap;
}
.tool-caret {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  z-index: 1;
}
.tool-caret:hover {
  background: rgba(255, 255, 255, 0.22);
}
.tool-divider {
  width: 1px;
  height: 40px;
  margin: 0 10px 18px;
  background: rgba(255, 255, 255, 0.12);
  align-self: flex-end;
}

.device-menu {
  min-width: 260px;
  max-width: 360px;
  background: #2c2c2c;
  border-radius: 10px;
  padding: 8px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.device-menu-title {
  padding: 6px 10px 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}
.device-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: #f0f0f0;
  text-align: left;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.device-item:hover,
.device-item.active {
  background: rgba(0, 110, 255, 0.2);
}
.check-spacer {
  width: 14px;
  flex-shrink: 0;
}
.device-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.members-panel,
.chat-panel-side {
  position: absolute;
  top: 52px;
  right: 0;
  bottom: 0;
  width: min(320px, 88vw);
  z-index: 20;
  background: #1e1e1e;
  border-left: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 24px rgba(0, 0, 0, 0.35);
}
.members-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.members-panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.members-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.members-close:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.members-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  overflow-y: auto;
  flex: 1;
}
.waiting-sep {
  padding: 10px 8px 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}
.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 8px;
}
.member-row:hover {
  background: rgba(255, 255, 255, 0.05);
}
.member-av {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.member-meta {
  min-width: 0;
  flex: 1;
}
.member-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  flex-wrap: wrap;
}
.member-name span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-status {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.55);
}
.member-status .off {
  color: #ff6b6b;
}
.member-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}
.member-actions button {
  border: none;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 11px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  white-space: nowrap;
}
.member-actions .danger {
  background: rgba(229, 72, 77, 0.85);
}
.member-actions .admit {
  background: #006eff;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat-empty {
  margin: 24px 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}
.chat-row.self {
  align-self: flex-end;
  text-align: right;
}
.chat-sender {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
  margin-bottom: 3px;
}
.chat-bubble {
  display: inline-block;
  max-width: 100%;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 13px;
  line-height: 1.4;
  word-break: break-word;
}
.chat-row.self .chat-bubble {
  background: rgba(0, 110, 255, 0.35);
}
.chat-compose {
  display: flex;
  gap: 8px;
  padding: 10px 12px 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.chat-input {
  flex: 1;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: #1f1f1f;
  color: #f0f0f0;
  padding: 8px 10px;
  font-size: 13px;
  outline: none;
}
.chat-input:focus {
  border-color: #006eff;
}
.chat-send {
  flex-shrink: 0;
  padding: 8px 12px;
}

@media (max-width: 720px) {
  .pip-strip {
    top: 8px;
    right: 8px;
  }
  .pip-tile {
    width: 120px;
  }
  .tool-btn {
    min-width: 60px;
    padding: 4px 6px;
  }
  .tool-icon {
    width: 40px;
    height: 40px;
  }
}
</style>
