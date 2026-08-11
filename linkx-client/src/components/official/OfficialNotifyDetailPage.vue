<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * LinkX 官方单条通知详情（完整正文）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../../stores/notifications'
import { getFeedbackDetail, type FeedbackVO } from '../../api/feedback'
import { resolveNoteMediaUrl } from '../../api/note'
import { normalizeMediaUrl } from '../../utils/mediaUrl'
import { useI18n } from '../../i18n'
import EmptyState from '../common/EmptyState.vue'
import {
  buildOfficialNotifyViewModel,
  officialTypeLabel,
  type OfficialBodyPart
} from '../../utils/officialNotifyContent'

const route = useRoute()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const { officialNotifs } = storeToRefs(notificationsStore)
const { fetchMessageNotifications, markMessageAsRead } = notificationsStore

const resolvedEvidenceUrls = ref<Record<string, string>>({})
const feedbackDetail = ref<FeedbackVO | null>(null)
const feedbackLoading = ref(false)

const notifId = computed(() => String(route.params.id || ''))

const notif = computed(() => officialNotifs.value.find(n => n.id === notifId.value))

const viewModel = computed(() => {
  if (!notif.value) return null
  return buildOfficialNotifyViewModel(notif.value, t)
})

const typeLabel = computed(() =>
  notif.value ? officialTypeLabel(t, notif.value.type) : ''
)

const feedbackStatusLabel = computed(() => {
  const status = feedbackDetail.value?.status
  if (!status) return ''
  switch (status) {
    case 'pending':
      return t('chat.officialFeedbackStatusPending')
    case 'processing':
      return t('chat.officialFeedbackStatusProcessing')
    case 'resolved':
      return t('chat.officialFeedbackStatusResolved')
    case 'replied':
      return t('chat.officialFeedbackStatusReplied')
    case 'closed':
      return t('chat.officialFeedbackStatusClosed')
    default:
      return status
  }
})

const feedbackReplies = computed(() => {
  const detail = feedbackDetail.value
  if (!detail) return []
  if (detail.replies?.length) return detail.replies
  if (detail.reply) {
    return [
      {
        id: 'legacy-reply',
        senderType: 'admin' as const,
        senderName: t('chat.officialReplyFromAdmin'),
        content: detail.reply,
        createTime: detail.replyTime || detail.createTime
      }
    ]
  }
  return []
})

function shouldLoadFeedbackDetail(type?: string, relatedId?: string) {
  if (!type || !relatedId) return false
  return type.startsWith('feedback_') || type.startsWith('review_')
}

async function loadFeedbackDetail(type?: string, relatedId?: string) {
  feedbackDetail.value = null
  if (!shouldLoadFeedbackDetail(type, relatedId)) return
  feedbackLoading.value = true
  try {
    const res = await getFeedbackDetail(relatedId!)
    if (res.code === 200 && res.data) {
      feedbackDetail.value = res.data
    }
  } catch {
    /* 无关联工单时仍展示通知原文 */
  } finally {
    feedbackLoading.value = false
  }
}

function replySenderLabel(senderType?: string, senderName?: string) {
  if (senderName) return senderName
  if (senderType === 'admin') return t('chat.officialReplyFromAdmin')
  if (senderType === 'user') return t('chat.officialReplyFromUser')
  return t('chat.officialReplyFromUnknown')
}

function formatReplyTime(raw?: string) {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}/${m}/${d} ${hh}:${mm}`
}

onMounted(async () => {
  await fetchMessageNotifications()
  if (notif.value?.readStatus === 0) {
    void markMessageAsRead(notifId.value)
  }
})

watch(notifId, async id => {
  if (!id) return
  await fetchMessageNotifications()
  const n = officialNotifs.value.find(x => x.id === id)
  if (n?.readStatus === 0) {
    void markMessageAsRead(id)
  }
})

watch(
  notif,
  n => {
    void loadFeedbackDetail(n?.type, n?.relatedId)
  },
  { immediate: true }
)

watch(
  viewModel,
  vm => {
    if (!vm) return
    void resolveEvidenceKeys(vm.images)
  },
  { immediate: true }
)

async function resolveEvidenceKeys(parts: OfficialBodyPart[]) {
  const keys = parts
    .filter(p => p.kind === 'image' && p.key && !resolvedEvidenceUrls.value[p.key])
    .map(p => p.key!)
  if (!keys.length) return
  await Promise.all(
    keys.map(async key => {
      try {
        const res = await resolveNoteMediaUrl(key)
        const url = normalizeMediaUrl(res.data) || res.data || ''
        if (res.code === 200 && url) {
          resolvedEvidenceUrls.value = { ...resolvedEvidenceUrls.value, [key]: url }
        }
      } catch {
        /* ignore */
      }
    })
  )
}
</script>

<template>
  <div class="official-detail-page">
    <EmptyState
      v-if="!viewModel"
      :title="t('chat.officialDetailMissing')"
      :description="t('chat.officialDetailMissingDesc')"
    />
    <article v-else class="detail-article">
      <header class="detail-head">
        <span v-if="typeLabel" class="detail-type">{{ typeLabel }}</span>
        <h1 class="detail-title">{{ viewModel.title }}</h1>
        <time class="detail-time">{{ viewModel.fullTime }}</time>
      </header>

      <section v-if="viewModel.body" class="detail-section">
        <h2 class="section-label">{{ t('chat.officialDetailSummary') }}</h2>
        <p class="detail-body">{{ viewModel.body }}</p>
      </section>

      <section v-if="viewModel.fields.length" class="detail-section">
        <h2 class="section-label">{{ t('chat.officialDetailFields') }}</h2>
        <dl class="detail-fields">
          <div v-for="(field, idx) in viewModel.fields" :key="idx" class="detail-field-row">
            <dt>{{ field.label }}</dt>
            <dd>{{ field.value }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="feedbackLoading" class="detail-section">
        <p class="detail-loading">{{ t('chat.officialDetailLoadingFeedback') }}</p>
      </section>

      <section v-else-if="feedbackDetail" class="detail-section">
        <h2 class="section-label">{{ t('chat.officialDetailFeedbackTicket') }}</h2>
        <dl class="detail-fields">
          <div v-if="feedbackStatusLabel" class="detail-field-row">
            <dt>{{ t('chat.officialDetailStatus') }}</dt>
            <dd>{{ feedbackStatusLabel }}</dd>
          </div>
          <div class="detail-field-row">
            <dt>{{ t('chat.officialDetailFeedbackFull') }}</dt>
            <dd>{{ feedbackDetail.content }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="feedbackReplies.length" class="detail-section">
        <h2 class="section-label">{{ t('chat.officialDetailReplyHistory') }}</h2>
        <ol class="detail-replies">
          <li v-for="reply in feedbackReplies" :key="reply.id" class="detail-reply-item">
            <div class="detail-reply-meta">
              <span class="detail-reply-sender">
                {{ replySenderLabel(reply.senderType, reply.senderName) }}
              </span>
              <time v-if="reply.createTime" class="detail-reply-time">
                {{ formatReplyTime(reply.createTime) }}
              </time>
            </div>
            <p class="detail-reply-content">{{ reply.content }}</p>
          </li>
        </ol>
      </section>

      <section v-if="viewModel.rawLines.length" class="detail-section">
        <h2 class="section-label">{{ t('chat.officialDetailFull') }}</h2>
        <div class="detail-raw">
          <p v-for="(line, idx) in viewModel.rawLines" :key="idx" class="detail-raw-line">{{ line }}</p>
        </div>
      </section>

      <section v-if="viewModel.images.length" class="detail-section">
        <h2 class="section-label">{{ t('chat.officialDetailImages') }}</h2>
        <div class="detail-images">
          <a
            v-for="(img, idx) in viewModel.images"
            :key="idx"
            class="detail-image-link"
            :href="img.key ? resolvedEvidenceUrls[img.key] : undefined"
            target="_blank"
            rel="noopener noreferrer"
          >
            <img
              v-if="img.key && resolvedEvidenceUrls[img.key]"
              :src="resolvedEvidenceUrls[img.key]"
              alt=""
            />
            <span v-else class="detail-image-pending">{{ img.key }}</span>
          </a>
        </div>
      </section>

      <p v-if="viewModel.footerHint" class="detail-footer-hint">{{ viewModel.footerHint }}</p>
    </article>
  </div>
</template>

<style scoped>
.official-detail-page {
  max-width: 640px;
  margin: 0 auto;
}

.detail-article {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-xl);
  padding: var(--lx-space-3xl) var(--lx-space-3xl-plus) var(--lx-space-5xl-minus);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

:global([data-theme='dark']) .detail-article {
  background: var(--lx-bg-card);
  box-shadow: none;
  border: 1px solid var(--lx-divider);
}

.detail-head {
  padding-bottom: var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-official-border);
}

:global([data-theme='dark']) .detail-head {
  border-bottom-color: var(--lx-divider);
}

.detail-type {
  display: inline-block;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-bottom: var(--lx-space);
}

.detail-title {
  margin: 0;
  font-size: var(--lx-font-4xl);
  font-weight: 600;
  line-height: var(--lx-leading);
  color: var(--lx-text-primary);
}

:global([data-theme='dark']) .detail-title {
  color: var(--lx-text-primary);
}

.detail-time {
  display: block;
  margin-top: var(--lx-space);
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.detail-section {
  margin-top: var(--lx-space-3xl);
}

.section-label {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font-md);
  font-weight: 600;
  color: var(--lx-text-muted);
}

.detail-body {
  margin: 0;
  font-size: var(--lx-font-lg);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-text-body);
  word-break: break-word;
}

:global([data-theme='dark']) .detail-body {
  color: var(--lx-text-body);
}

.detail-fields {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-md);
}

.detail-field-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: var(--lx-space) var(--lx-space-lg);
  font-size: var(--lx-font);
  line-height: var(--lx-leading-normal);
}

.detail-field-row dt {
  margin: 0;
  color: var(--lx-text-muted);
}

.detail-field-row dd {
  margin: 0;
  color: var(--lx-text-body);
  word-break: break-word;
}

:global([data-theme='dark']) .detail-field-row dd {
  color: var(--lx-text-body);
}

.detail-raw {
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius-sm);
  padding: var(--lx-space-lg) var(--lx-space-xl);
}

:global([data-theme='dark']) .detail-raw {
  background: rgba(255, 255, 255, 0.04);
}

.detail-raw-line {
  margin: 0;
  font-size: var(--lx-font);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-text-body);
  word-break: break-word;
}

.detail-raw-line + .detail-raw-line {
  margin-top: var(--lx-space-sm);
}

:global([data-theme='dark']) .detail-raw-line {
  color: var(--lx-text-body);
}

.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space-md);
}

.detail-image-link {
  display: block;
  border-radius: var(--lx-radius-sm);
  overflow: hidden;
  border: 1px solid var(--lx-official-border-soft);
  line-height: 0;
}

.detail-image-link img {
  width: 160px;
  height: 160px;
  object-fit: cover;
  display: block;
}

.detail-image-pending {
  display: block;
  padding: var(--lx-space-lg);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.detail-footer-hint {
  margin: var(--lx-space-4xl) 0 0;
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
}

.detail-loading {
  margin: 0;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.detail-replies {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
}

.detail-reply-item {
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius-sm);
  padding: var(--lx-space-lg) var(--lx-space-xl);
}

:global([data-theme='dark']) .detail-reply-item {
  background: rgba(255, 255, 255, 0.04);
}

.detail-reply-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
  margin-bottom: var(--lx-space-sm);
}

.detail-reply-sender {
  font-size: var(--lx-font-md);
  font-weight: 600;
  color: var(--lx-text-body);
}

:global([data-theme='dark']) .detail-reply-sender {
  color: var(--lx-text-primary);
}

.detail-reply-time {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.detail-reply-content {
  margin: 0;
  font-size: var(--lx-font);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-text-body);
  word-break: break-word;
}

:global([data-theme='dark']) .detail-reply-content {
  color: var(--lx-text-body);
}
</style>
