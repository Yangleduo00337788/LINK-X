<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 文本消息气泡。
 * <p>
 * 展示纯文本或链接样式，支持回复引用预览；群聊 @ 提及高亮显示。
 * </p>
 */
import { NIcon } from 'naive-ui'
import { LinkOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import { useGroupMetaStore } from '../../../stores/groupMeta'
import { splitMentionContent } from '../../../utils/messageNotify'
import { chatMessagePreviewText } from '../../../utils/messagePreviewText'
import { useI18n } from '../../../i18n'
import { useMessageTranslationStore } from '../../../stores/messageTranslation'
import QuoteReplyBar from '../QuoteReplyBar.vue'

const { t } = useI18n()
const translationStore = useMessageTranslationStore()

const props = defineProps<{ msg: ChatMessage }>()

const appStore = useAppStore()
const { userProfile, currentSession } = storeToRefs(appStore)
const groupMetaStore = useGroupMetaStore()

/** 是否为链接类消息：type=link、含 http(s) URL 或含「抖音」关键字 */
const isLinkMsg = computed(() => {
  const msg = props.msg
  return msg.type === 'link' || /https?:\/\//.test(msg.content) || msg.content.includes('抖音')
})

/** 拆分正文，高亮 @成员 / @全体成员；@到自己时额外强调 */
const contentSegments = computed(() =>
  splitMentionContent(props.msg.content || '', [
    userProfile.value.nickname,
    userProfile.value.username
  ])
)

const replyPreviewText = computed(() => {
  const reply = props.msg.replyTo
  if (!reply) return ''
  return chatMessagePreviewText(reply)
})

const replySenderName = computed(() => {
  const reply = props.msg.replyTo
  if (!reply) return ''
  if (reply.senderName?.trim()) return reply.senderName.trim()

  const myId = userProfile.value.userId ? String(userProfile.value.userId) : ''
  const senderId = reply.senderId ? String(reply.senderId) : ''

  if (senderId && myId && senderId === myId) {
    return userProfile.value.nickname?.trim() || t('chat.me')
  }

  if (senderId) {
    const member = groupMetaStore.membersFor(props.msg.sessionId).find(m => m.id === senderId)
    if (member?.name) return member.name
    const session = currentSession.value
    if (session?.peerUserId && senderId === String(session.peerUserId)) {
      return session.name || ''
    }
  }

  if (reply.isSelf) {
    return userProfile.value.nickname?.trim() || t('chat.me')
  }

  return currentSession.value?.name || ''
})

const hasReply = computed(() => !!props.msg.replyTo)

const translationEntry = computed(() => translationStore.getEntry(props.msg.id))

const showTranslation = computed(
  () =>
    !!translationEntry.value?.visible &&
    (!!translationEntry.value.loading || !!translationEntry.value.text || !!translationEntry.value.error)
)
</script>

<template>
  <div class="text-message-stack" :class="{ 'text-message-stack--self': msg.isSelf }">
    <!-- 正文气泡 -->
    <div class="lx-bubble" :class="{ self: msg.isSelf, link: isLinkMsg }">
      <p class="lx-bubble-text">
        <template v-for="(seg, i) in contentSegments" :key="i">
          <span
            v-if="seg.mention"
            class="lx-mention"
            :class="{ 'lx-mention--me': seg.atMe }"
          >{{ seg.text }}</span>
          <template v-else>{{ seg.text }}</template>
        </template>
      </p>
      <n-icon v-if="isLinkMsg" class="lx-link-ico" :component="LinkOutline" :size="14" />
    </div>
    <!-- 灰条引用预览在气泡下方 -->
    <QuoteReplyBar
      v-if="hasReply"
      variant="below"
      :sender-name="replySenderName"
      :content="replyPreviewText"
    />
    <div
      v-if="showTranslation"
      class="lx-translation"
      :class="{ 'lx-translation--self': msg.isSelf }"
    >
      <div class="lx-translation-label">{{ t('chat.translationLabel') }}</div>
      <p v-if="translationEntry?.loading" class="lx-translation-text is-loading">
        {{ t('chat.translating') }}
      </p>
      <p v-else-if="translationEntry?.error" class="lx-translation-text is-error">
        {{ translationEntry.error }}
      </p>
      <p v-else class="lx-translation-text">{{ translationEntry?.text }}</p>
    </div>
  </div>
</template>

<style scoped>
.text-message-stack {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
  max-width: 100%;
}

.text-message-stack--self {
  align-items: flex-end;
}

.lx-translation {
  max-width: 100%;
  padding: var(--lx-space-sm) var(--lx-space);
  border-radius: var(--lx-radius-md);
  background: var(--lx-quote-bg);
  border: 1px solid var(--lx-border-subtle);
}

.lx-translation--self {
  align-self: flex-end;
}

.lx-translation-label {
  margin-bottom: var(--lx-space-xs);
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
}

.lx-translation-text {
  margin: 0;
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.lx-translation-text.is-loading {
  color: var(--lx-text-muted);
}

.lx-translation-text.is-error {
  color: var(--lx-danger);
}
</style>
