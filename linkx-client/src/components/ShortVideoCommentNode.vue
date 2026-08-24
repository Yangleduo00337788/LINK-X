<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { Heart, HeartOutline } from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import { useI18n } from '../i18n'
import { resolveUserAvatarUrl } from '../utils/defaultAvatar'
import { isEncryptedShortVideoText, readableShortVideoText } from '../utils/shortVideoText'
import { resolveShortVideoCommentImageSrc } from '../utils/shortVideoMediaAccess'
import type { ShortVideoComment } from '../api/shortVideo'
import type { ShortVideoCommentNode } from '../utils/shortVideoComments'
import type { ShortVideoPost } from '../api/shortVideo'

defineOptions({ name: 'ShortVideoCommentNode' })

const props = defineProps<{
  node: ShortVideoCommentNode
  post: ShortVideoPost
  currentUserId: string
  isReply?: boolean
  highlightCommentId?: string | null
}>()

const emit = defineEmits<{
  reply: [comment: ShortVideoComment]
  delete: [comment: ShortVideoComment]
  like: [comment: ShortVideoComment]
}>()

const { t } = useI18n()
const repliesExpanded = ref(false)

function containsHighlight(node: ShortVideoCommentNode, id: string): boolean {
  if (node.id === id) return true
  return node.replies.some(reply => containsHighlight(reply, id))
}

const isHighlighted = computed(() =>
  Boolean(props.highlightCommentId && props.node.id === props.highlightCommentId)
)

watch(
  () => props.highlightCommentId,
  id => {
    if (!id || props.isReply) return
    if (containsHighlight(props.node, id)) {
      repliesExpanded.value = true
    }
  },
  { immediate: true }
)

const visibleReplies = computed(() => (repliesExpanded.value ? props.node.replies : []))

const hiddenReplyCount = computed(() => (repliesExpanded.value ? 0 : props.node.replies.length))

function isOwnComment(comment: ShortVideoComment) {
  return Boolean(comment.userId && props.currentUserId && comment.userId === props.currentUserId)
}

const commentImageSrc = computed(() => resolveShortVideoCommentImageSrc(props.node))

function displayContent(comment: ShortVideoComment) {
  const readable = readableShortVideoText(comment.content)
  if (readable) return readable
  if (isEncryptedShortVideoText(comment.content)) {
    return t('shortVideo.encryptedComment')
  }
  return ''
}

function formatLikeCount(n?: number) {
  const value = n ?? 0
  if (value <= 0) return ''
  if (value >= 10000) return `${Math.floor(value / 10000)}万+`
  return String(value)
}
</script>

<template>
  <div
    :id="`sv-comment-${node.id}`"
    class="sv-comment-node"
    :class="{
      'sv-comment-node--nested': isReply,
      'sv-comment-node--highlight': isHighlighted
    }"
    :style="{ marginLeft: isReply ? '28px' : undefined }"
  >
    <div class="sv-comment-node__row">
      <Avatar
        :text="(node.nickname || t('shortVideo.author')).slice(0, 1)"
        color="transparent"
        :size="isReply ? 28 : 36"
        :image-url="resolveUserAvatarUrl(node.avatar, node.userId)"
      />
      <div class="sv-comment-node__main">
        <div class="sv-comment-node__meta">
          <span class="sv-comment-node__name">{{ node.nickname || t('shortVideo.author') }}</span>
          <span class="sv-comment-node__time">{{ node.time }}</span>
        </div>
        <p v-if="displayContent(node)" class="sv-comment-node__content">
          <span v-if="node.replyToNickname" class="sv-comment-node__reply-tag">
            {{ t('moments.reply') }} {{ node.replyToNickname }}：
          </span>
          {{ displayContent(node) }}
        </p>
        <img
          v-if="commentImageSrc"
          :src="commentImageSrc"
          class="sv-comment-node__image"
          alt=""
          loading="lazy"
        />
        <div class="sv-comment-node__actions">
          <button
            type="button"
            class="sv-comment-node__like"
            :class="{ 'sv-comment-node__like--active': node.liked }"
            @click="emit('like', node)"
          >
            <NIcon :component="node.liked ? Heart : HeartOutline" :size="14" />
            <span v-if="(node.likes ?? 0) > 0">{{ formatLikeCount(node.likes) }}</span>
          </button>
          <button type="button" class="sv-comment-node__btn" @click="emit('reply', node)">
            {{ t('moments.reply') }}
          </button>
          <button
            v-if="isOwnComment(node)"
            type="button"
            class="sv-comment-node__btn sv-comment-node__btn--danger"
            @click="emit('delete', node)"
          >
            {{ t('common.delete') }}
          </button>
        </div>
      </div>
    </div>

    <template v-if="!isReply && node.replies.length > 0">
      <button
        v-if="hiddenReplyCount > 0"
        type="button"
        class="sv-comment-node__toggle"
        @click="repliesExpanded = true"
      >
        {{ t('shortVideo.expandReplies', { n: hiddenReplyCount }) }}
      </button>
      <button
        v-else-if="repliesExpanded"
        type="button"
        class="sv-comment-node__toggle"
        @click="repliesExpanded = false"
      >
        {{ t('shortVideo.collapseReplies') }}
      </button>
      <div v-if="repliesExpanded" class="sv-comment-node__replies">
        <ShortVideoCommentNode
          v-for="reply in visibleReplies"
          :key="reply.id"
          :node="reply"
          :post="post"
          :current-user-id="currentUserId"
          :highlight-comment-id="highlightCommentId"
          is-reply
          @reply="emit('reply', $event)"
          @delete="emit('delete', $event)"
          @like="emit('like', $event)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.sv-comment-node {
  padding: 8px 0;
}

.sv-comment-node--nested {
  padding-top: 6px;
}

.sv-comment-node--highlight .sv-comment-node__main {
  border-radius: 8px;
  background: rgba(254, 44, 85, 0.12);
  box-shadow: inset 0 0 0 1px rgba(254, 44, 85, 0.35);
  padding: 6px 8px;
  margin: -6px -8px;
}

.sv-comment-node__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.sv-comment-node__main {
  flex: 1;
  min-width: 0;
}

.sv-comment-node__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.sv-comment-node__name {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.55);
}

.sv-comment-node__time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
}

.sv-comment-node__content {
  margin: 0;
  font-size: 14px;
  line-height: 1.45;
  color: rgba(255, 255, 255, 0.92);
  word-break: break-word;
}

.sv-comment-node__reply-tag {
  color: rgba(255, 255, 255, 0.55);
}

.sv-comment-node__image {
  display: block;
  max-width: 140px;
  max-height: 140px;
  margin-top: 6px;
  border-radius: 8px;
  object-fit: cover;
  cursor: pointer;
}

.sv-comment-node__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}

.sv-comment-node__like {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.sv-comment-node__like--active {
  color: #fe2c55;
}

.sv-comment-node__btn {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.sv-comment-node__btn--danger {
  color: #ff8a8a;
}

.sv-comment-node__replies {
  margin-top: 2px;
}

.sv-comment-node__toggle {
  margin: 4px 0 4px 46px;
  border: none;
  background: transparent;
  color: rgba(110, 182, 255, 0.95);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
</style>
