<script setup lang="ts">
/**
 * 多人会议房（腾讯会议演讲者视图）：主持人全屏，其他人右上角小窗；本人显示本地摄像头。
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
  CheckmarkOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useConferenceStore } from '../../stores/conference'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'
import { generateDefaultAvatar } from '../../utils/defaultAvatar'

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
  audioInputs,
  videoInputs,
  selectedAudioId,
  selectedVideoId,
  myUserId
} = storeToRefs(conferenceStore)

const localVideoRef = ref<HTMLVideoElement | null>(null)
const joinedAt = ref(0)
const elapsedSec = ref(0)
const membersPanelOpen = ref(false)
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
    const avatar =
      (p.avatar && String(p.avatar)) ||
      member?.avatarUrl ||
      (isMe ? appStore.userProfile.avatarUrl : '') ||
      generateDefaultAvatar(displayName || uid, 160)
    return {
      ...p,
      userId: uid,
      isMe,
      isHostRole: p.role === 'host' || uid === String(conferenceStore.creatorId),
      displayName,
      avatar
    }
  })
})

watch(
  () => (phase.value === 'in_room' ? conferenceStore.conversationId : null),
  id => {
    if (id) void groupMeta.fetchMembers(id)
  },
  { immediate: true }
)

/** 演讲者视图：主持人占主画面，其余成员小窗 */
const speaker = computed(() => {
  const list = displayParticipants.value
  return list.find(p => p.isHostRole) || list.find(p => p.isMe) || list[0] || null
})

const pipParticipants = computed(() => {
  const mainId = speaker.value?.userId
  return displayParticipants.value.filter(p => p.userId !== mainId)
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

function hasLiveRemoteVideo(userId: string, videoOff?: boolean): boolean {
  if (videoOff) return false
  const stream = remoteStreamOf(userId)
  if (!stream) return false
  return stream.getVideoTracks().some(t => t.readyState === 'live' && t.enabled)
}

function isMuted(p: { isMe: boolean; muted?: boolean }) {
  return p.isMe ? !micOn.value : !!p.muted
}

function isVideoOff(p: { isMe: boolean; videoOff?: boolean }) {
  return p.isMe ? !cameraOn.value : !!p.videoOff
}

function showLocalVideo(p: { isMe: boolean; videoOff?: boolean }) {
  return p.isMe && type.value === 'video' && cameraOn.value && !p.videoOff
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

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    conferenceStore.clearError()
  }
})

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
  [localStream, cameraOn, () => speaker.value?.userId, () => pipParticipants.value.map(p => p.userId).join(',')],
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

async function acceptInvite() {
  const prompt = invitePrompt.value
  if (!prompt) return
  try {
    await conferenceStore.joinExisting(prompt.conferenceId, String(appStore.userProfile.userId || ''))
  } catch (e) {
    message.error((e as Error).message || t('conference.joinFail'))
  }
}

async function leave() {
  await conferenceStore.leave()
}

async function endMeeting() {
  try {
    await conferenceStore.endAsHost()
  } catch (e) {
    message.error((e as Error).message || t('conference.endFail'))
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

    <div
      v-if="visible && phase === 'in_room'"
      class="room-root"
      :class="{ 'room-root--members-open': membersPanelOpen }"
    >
      <!-- 顶栏：会议名 / 人数 / 时长 -->
      <header class="room-header">
        <div class="header-left">
          <div class="title-block">
            <h2>{{ title }}</h2>
            <div class="meta-row">
              <span>{{ t('conference.memberCount', { n: displayParticipants.length }) }}</span>
              <span class="dot">·</span>
              <span class="timer">{{ elapsedLabel }}</span>
              <span v-if="networkHint" class="hint">{{ networkHint }}</span>
            </div>
          </div>
        </div>
        <button type="button" class="header-close" :title="t('conference.leave')" @click="leave">
          <n-icon :component="CloseOutline" :size="20" />
        </button>
      </header>

      <!-- 演讲者视图：主持人全屏，其他人右上角小窗 -->
      <div class="stage">
        <div v-if="speaker" class="speaker-stage">
          <!-- 主画面：主持人 -->
          <div class="main-tile" :class="{ me: speaker.isMe }">
            <video
              v-if="showLocalVideo(speaker)"
              :ref="bindLocalVideo"
              class="tile-video tile-video--local"
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
              <img :src="speaker.avatar" class="tile-avatar tile-avatar--lg" alt="" />
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
                  v-if="isVideoOff(speaker)"
                  class="cam-off"
                  :component="VideocamOff"
                  :size="14"
                />
                <span>{{ participantLabel(speaker) }}</span>
                <span class="host-badge">{{ t('conference.host') }}</span>
              </div>
            </div>
          </div>

          <!-- 其他人：右上角小屏幕 -->
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
                class="tile-video tile-video--local"
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
                <img :src="p.avatar" class="tile-avatar" alt="" />
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
                  <span>{{ participantLabel(p) }}</span>
                </div>
                <div v-if="isHost && !p.isMe" class="tile-host-actions">
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

      <!-- 底栏：腾讯会议式图标+文案 -->
      <footer class="toolbar">
        <div class="toolbar-inner">
          <!-- 麦克风 + 设备上拉 -->
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

          <!-- 摄像头 + 设备上拉 -->
          <div v-if="type === 'video'" class="tool-item-wrap">
            <button
              type="button"
              class="tool-btn"
              :class="{ off: !cameraOn }"
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
              v-if="videoOptions.length"
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
            :class="{ active: membersPanelOpen }"
            @click="membersPanelOpen = !membersPanelOpen"
          >
            <span class="tool-icon">
              <n-icon :component="PeopleOutline" :size="22" />
            </span>
            <span class="tool-label">
              {{ t('conference.members') }} ({{ displayParticipants.length }})
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
          <li v-for="p in displayParticipants" :key="p.userId" class="member-row">
            <img :src="p.avatar" class="member-av" alt="" />
            <div class="member-meta">
              <div class="member-name">
                <span>{{ participantLabel(p) }}</span>
                <span v-if="p.isHostRole" class="host-badge">{{ t('conference.host') }}</span>
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
            <div v-if="isHost && !p.isMe" class="member-actions">
              <button type="button" @click="conferenceStore.muteTarget(p.userId, !p.muted)">
                {{ p.muted ? t('conference.unmute') : t('conference.mute') }}
              </button>
              <button type="button" class="danger" @click="conferenceStore.removeTarget(p.userId)">
                {{ t('conference.remove') }}
              </button>
            </div>
          </li>
        </ul>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.invite-mask {
  position: fixed;
  inset: 0;
  z-index: 12000;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}
.invite-card {
  width: min(360px, 90vw);
  background: #2b2b2b;
  border-radius: 12px;
  padding: 22px;
  color: #f0f0f0;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
}
.invite-card h3 {
  margin: 0 0 8px;
  font-size: 17px;
}
.invite-card p {
  margin: 0 0 18px;
  color: rgba(255, 255, 255, 0.65);
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

/* ---- 顶栏 ---- */
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

/* ---- 演讲者舞台：主持人全屏 + 右上角小窗 ---- */
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
.room-root--members-open .pip-strip {
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
.mic-off {
  color: #ff6b6b;
  flex-shrink: 0;
}
.cam-off {
  color: #ff6b6b;
  flex-shrink: 0;
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

/* ---- 底栏 ---- */
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

/* ---- 成员面板 ---- */
.members-panel {
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
