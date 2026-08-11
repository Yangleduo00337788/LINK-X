<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 会话顶栏进行中条：语音电话 / 视频电话 / 会议 三者分开展示，点击加入或返回。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { CallOutline, PeopleOutline, VideocamOutline } from '@vicons/ionicons5'
import { useI18n } from '../../i18n'
import type { SessionActiveConference } from '../../stores/conference'

export type SessionBannerInfo =
  | (SessionActiveConference & { kind: 'conference' })
  | {
      kind: 'private_call'
      conversationId: string
      type: 'voice' | 'video'
      title: string
      participantCount?: number
    }

const props = defineProps<{
  info: SessionBannerInfo
  /** 当前用户是否已在该通话/会议中 */
  inRoom: boolean
}>()

defineEmits<{ (e: 'join'): void }>()

const { t } = useI18n()

const isVoice = computed(() => props.info.type === 'voice')
const isMeeting = computed(
  () => props.info.kind === 'conference' && props.info.scene === 'meeting'
)

const statusText = computed(() => {
  if (props.info.kind === 'private_call') {
    return isVoice.value
      ? t('conference.bannerVoiceCallOngoing')
      : t('conference.bannerVideoCallOngoing')
  }
  if (props.info.scene === 'meeting') {
    return t('conference.bannerMeetingOngoing')
  }
  return isVoice.value
    ? t('conference.bannerVoiceCallOngoing')
    : t('conference.bannerVideoCallOngoing')
})

const countText = computed(() => {
  const n = props.info.participantCount
  if (n == null || n < 1) return ''
  return t('conference.bannerMemberCount', { n })
})

const actionText = computed(() =>
  props.inRoom ? t('conference.bannerBack') : t('conference.bannerJoin')
)

const actionAriaLabel = computed(() =>
  props.inRoom
    ? `${statusText.value}，${t('conference.bannerBack')}`
    : `${statusText.value}，${t('conference.bannerJoin')}`
)

const icon = computed(() => {
  if (isMeeting.value) return PeopleOutline
  return isVoice.value ? CallOutline : VideocamOutline
})

const toneClass = computed(() => {
  if (isMeeting.value) return 'is-meeting'
  return isVoice.value ? 'is-voice' : 'is-video'
})
</script>

<template>
  <div class="conf-banner" :class="toneClass" role="status">
    <div class="conf-banner-main">
      <span class="conf-banner-icon" aria-hidden="true">
        <n-icon :component="icon" :size="16" />
      </span>
      <div class="conf-banner-text">
        <span class="conf-banner-status">{{ statusText }}</span>
        <span v-if="countText" class="conf-banner-count">{{ countText }}</span>
        <span v-if="info.title" class="conf-banner-title">{{ info.title }}</span>
      </div>
    </div>
    <button type="button" class="conf-banner-btn" :aria-label="actionAriaLabel" @click="$emit('join')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.conf-banner {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
  min-height: 36px;
  padding: var(--lx-space-sm) var(--lx-space-xl) var(--lx-space-sm) var(--lx-space-2xl);
  position: relative;
  z-index: var(--lx-z-dock);
  border-bottom: 1px solid transparent;
}
.conf-banner.is-voice {
  background: linear-gradient(90deg, rgba(18, 183, 106, 0.12), rgba(18, 183, 106, 0.05));
  border-bottom-color: rgba(18, 183, 106, 0.18);
  color: var(--lx-conf-voice-text);
}
.conf-banner.is-video {
  background: linear-gradient(90deg, rgba(26, 107, 255, 0.12), rgba(26, 107, 255, 0.05));
  border-bottom-color: rgba(26, 107, 255, 0.18);
  color: var(--lx-conf-video-text);
}
.conf-banner.is-meeting {
  background: linear-gradient(90deg, rgba(124, 58, 237, 0.12), rgba(124, 58, 237, 0.05));
  border-bottom-color: rgba(124, 58, 237, 0.18);
  color: var(--lx-conf-meeting-text);
}
.conf-banner-main {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  min-width: 0;
  flex: 1;
}
.conf-banner-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-on-accent);
}
.conf-banner.is-voice .conf-banner-icon {
  background: var(--lx-conf-voice);
}
.conf-banner.is-video .conf-banner-icon {
  background: var(--lx-conf-video);
}
.conf-banner.is-meeting .conf-banner-icon {
  background: var(--lx-conf-meeting);
}
.conf-banner-text {
  display: flex;
  align-items: baseline;
  gap: var(--lx-space);
  min-width: 0;
  flex-wrap: wrap;
}
.conf-banner-status {
  font-size: var(--lx-font-md);
  font-weight: 600;
  white-space: nowrap;
}
.conf-banner-count {
  font-size: var(--lx-font-sm);
  opacity: 0.85;
  white-space: nowrap;
}
.conf-banner-title {
  font-size: var(--lx-font-sm);
  opacity: 0.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.conf-banner-btn {
  flex-shrink: 0;
  height: 26px;
  padding: 0 var(--lx-space-lg);
  border: none;
  border-radius: var(--lx-radius-lg);
  font-size: var(--lx-font-sm);
  font-weight: 600;
  cursor: pointer;
  color: var(--lx-text-on-accent);
}
.conf-banner.is-voice .conf-banner-btn {
  background: var(--lx-conf-voice);
}
.conf-banner.is-video .conf-banner-btn {
  background: var(--lx-conf-video);
}
.conf-banner.is-meeting .conf-banner-btn {
  background: var(--lx-conf-meeting);
}
.conf-banner-btn:hover {
  filter: brightness(1.05);
}
</style>
