<script setup lang="ts">
/**
 * 多人会议房：成员网格、静音/视频、设备切换、主持人操作。
 */
import { ref, watch, computed, nextTick } from 'vue'
import { NIcon, NSelect, useMessage } from 'naive-ui'
import {
  Mic,
  MicOff,
  Videocam,
  VideocamOff,
  Call,
  PeopleOutline,
  SwapHorizontalOutline
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

const displayParticipants = computed(() => {
  const sid = conferenceStore.conversationId || ''
  const members = groupMeta.members[sid] || []
  return participants.value.map(p => {
    const uid = String(p.userId)
    const member = members.find(m => String(m.id) === uid)
    return {
      ...p,
      userId: uid,
      displayName:
        member?.name ||
        p.nickname ||
        (uid === myUserId.value ? appStore.userProfile.nickname || t('moments.user') : t('moments.user')),
      avatar:
        member?.avatarUrl ||
        generateDefaultAvatar(member?.name || p.nickname || uid, 96)
    }
  })
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

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    conferenceStore.clearError()
  }
})

watch(
  localStream,
  async stream => {
    await nextTick()
    const el = localVideoRef.value
    if (!el) return
    el.srcObject = stream
    el.muted = true
    try {
      await el.play()
    } catch {
      /* ignore */
    }
  },
  { immediate: true }
)

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
        <h3>{{ t('conference.inviteTitle') }}</h3>
        <p>{{ invitePrompt.title }}</p>
        <div class="invite-actions">
          <button type="button" class="btn ghost" @click="conferenceStore.dismissInvite()">
            {{ t('common.cancel') }}
          </button>
          <button type="button" class="btn primary" @click="acceptInvite">
            {{ t('conference.join') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="visible && phase === 'in_room'" class="room-root">
      <header class="room-header">
        <div class="title-wrap">
          <n-icon :component="PeopleOutline" :size="20" />
          <div>
            <h2>{{ title }}</h2>
            <p>{{ t('conference.memberCount', { n: displayParticipants.length }) }}</p>
          </div>
        </div>
        <p v-if="networkHint" class="hint">{{ networkHint }}</p>
      </header>

      <div class="stage">
        <div class="self-preview">
          <video
            v-if="type === 'video' && cameraOn"
            ref="localVideoRef"
            class="self-video"
            autoplay
            playsinline
            muted
          />
          <div v-else class="self-avatar">
            <img
              :src="generateDefaultAvatar(appStore.userProfile.nickname || 'me', 120)"
              alt=""
            />
            <span>{{ t('conference.you') }}</span>
          </div>
        </div>

        <div class="grid">
          <div
            v-for="p in displayParticipants"
            :key="p.userId"
            class="tile"
            :class="{ me: p.userId === myUserId, muted: p.muted }"
          >
            <img :src="p.avatar" class="tile-av" alt="" />
            <div class="tile-meta">
              <span class="name">{{ p.displayName }}</span>
              <span v-if="p.role === 'host'" class="badge">{{ t('conference.host') }}</span>
              <n-icon v-if="p.muted" :component="MicOff" :size="14" />
              <n-icon v-if="p.videoOff" :component="VideocamOff" :size="14" />
            </div>
            <div v-if="isHost && p.userId !== myUserId" class="tile-actions">
              <button type="button" @click="conferenceStore.muteTarget(p.userId, !p.muted)">
                {{ p.muted ? t('conference.unmute') : t('conference.mute') }}
              </button>
              <button type="button" class="danger" @click="conferenceStore.removeTarget(p.userId)">
                {{ t('conference.remove') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <footer class="toolbar">
        <div class="devices" v-if="audioOptions.length || videoOptions.length">
          <n-select
            v-if="audioOptions.length"
            size="tiny"
            :value="selectedAudioId"
            :options="audioOptions"
            :placeholder="t('conference.mic')"
            @update:value="(v: string) => conferenceStore.switchAudioDevice(v)"
          />
          <n-select
            v-if="type === 'video' && videoOptions.length"
            size="tiny"
            :value="selectedVideoId"
            :options="videoOptions"
            :placeholder="t('conference.camera')"
            @update:value="(v: string) => conferenceStore.switchVideoDevice(v)"
          />
        </div>
        <div class="controls">
          <button type="button" class="ctrl" :class="{ off: !micOn }" @click="conferenceStore.toggleMic()">
            <n-icon :component="micOn ? Mic : MicOff" :size="22" />
          </button>
          <button
            v-if="type === 'video'"
            type="button"
            class="ctrl"
            :class="{ off: !cameraOn }"
            @click="conferenceStore.toggleCamera()"
          >
            <n-icon :component="cameraOn ? Videocam : VideocamOff" :size="22" />
          </button>
          <button type="button" class="ctrl hang" @click="leave">
            <n-icon :component="Call" :size="22" />
          </button>
          <button v-if="isHost" type="button" class="ctrl end" @click="endMeeting">
            <n-icon :component="SwapHorizontalOutline" :size="18" />
            <span>{{ t('conference.end') }}</span>
          </button>
        </div>
      </footer>
    </div>
  </Teleport>
</template>

<style scoped>
.invite-mask {
  position: fixed;
  inset: 0;
  z-index: 12000;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}
.invite-card {
  width: min(360px, 90vw);
  background: var(--lx-bg-card);
  border-radius: 14px;
  padding: 22px;
  color: var(--lx-text-body);
}
.invite-card h3 {
  margin: 0 0 8px;
}
.invite-card p {
  margin: 0 0 18px;
  color: var(--lx-text-muted);
}
.invite-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.btn {
  border: none;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
}
.btn.ghost {
  background: transparent;
  color: var(--lx-text-muted);
}
.btn.primary {
  background: var(--lx-accent);
  color: #fff;
}

.room-root {
  position: fixed;
  inset: 0;
  z-index: 11900;
  background: #0f1419;
  color: #f5f7fa;
  display: flex;
  flex-direction: column;
}
.room-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.title-wrap {
  display: flex;
  gap: 12px;
  align-items: center;
}
.title-wrap h2 {
  margin: 0;
  font-size: 18px;
}
.title-wrap p {
  margin: 2px 0 0;
  font-size: 12px;
  opacity: 0.7;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: #ffb454;
}
.stage {
  flex: 1;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  padding: 16px;
  min-height: 0;
}
.self-preview {
  border-radius: 14px;
  overflow: hidden;
  background: #1a222c;
  display: flex;
  align-items: center;
  justify-content: center;
}
.self-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.self-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.self-avatar img {
  width: 96px;
  height: 96px;
  border-radius: 50%;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  align-content: start;
  overflow: auto;
}
.tile {
  background: #1a222c;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 150px;
}
.tile.me {
  outline: 1px solid var(--lx-accent);
}
.tile-av {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
}
.tile-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 13px;
}
.badge {
  font-size: 11px;
  background: rgba(255, 255, 255, 0.12);
  padding: 1px 6px;
  border-radius: 999px;
}
.tile-actions {
  display: flex;
  gap: 6px;
  margin-top: auto;
}
.tile-actions button {
  border: none;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}
.tile-actions .danger {
  background: rgba(255, 80, 80, 0.25);
}
.toolbar {
  padding: 14px 20px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
.devices {
  display: flex;
  gap: 10px;
  width: min(520px, 100%);
}
.controls {
  display: flex;
  gap: 12px;
  align-items: center;
}
.ctrl {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.ctrl.off {
  background: rgba(255, 80, 80, 0.35);
}
.ctrl.hang {
  background: #e5484d;
  transform: rotate(135deg);
}
.ctrl.end {
  width: auto;
  padding: 0 14px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.16);
}
@media (max-width: 800px) {
  .stage {
    grid-template-columns: 1fr;
  }
  .self-preview {
    min-height: 180px;
  }
}
</style>
