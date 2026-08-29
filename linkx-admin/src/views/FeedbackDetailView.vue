<!-- 作者：yangleduo -->
<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NInput,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  useDialog,
  useMessage,
  type SelectOption,
} from 'naive-ui'
import {
  assignFeedback,
  closeFeedback,
  getFeedback,
  reopenFeedback,
  replyFeedback,
  type FeedbackItem,
} from '@/api/feedback'
import { listUsers } from '@/api/users'
import { displayOrNone, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const notFound = ref(false)
const feedback = ref<FeedbackItem | null>(null)
const assigneeOptions = ref<SelectOption[]>([])

const feedbackId = computed(() => String(route.params.id || ''))

const showAssign = ref(false)
const assigneeId = ref<string | null>(null)
const assignSaving = ref(false)

const showReply = ref(false)
const replyContent = ref('')
const replySaving = ref(false)

function typeLabel(type?: string) {
  void locale.value
  if (type === 'bug') return t('feedback.typeBug')
  if (type === 'suggestion') return t('feedback.typeSuggestion')
  if (type === 'other') return t('feedback.typeOther')
  return displayOrNone(type)
}

async function loadAssigneeOptions() {
  const data = await listUsers({ page: 1, size: 100 })
  assigneeOptions.value = (data.items || []).map((u) => ({
    label: u.nickname || u.username || u.id,
    value: u.id,
  }))
}

async function load() {
  if (!feedbackId.value) return
  loading.value = true
  notFound.value = false
  try {
    feedback.value = await getFeedback(feedbackId.value)
  } catch {
    feedback.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

function openAssign() {
  if (!feedback.value) return
  assigneeId.value = feedback.value.assigneeId || null
  showAssign.value = true
}

async function submitAssign() {
  if (!feedback.value) return
  assignSaving.value = true
  try {
    await assignFeedback(feedback.value.id, assigneeId.value)
    message.success(t('feedback.assignSuccess'))
    showAssign.value = false
    await load()
  } finally {
    assignSaving.value = false
  }
}

function openReply() {
  replyContent.value = ''
  showReply.value = true
}

async function submitReply() {
  if (!feedback.value || !replyContent.value.trim()) {
    message.warning(t('feedback.replyRequired'))
    return
  }
  replySaving.value = true
  try {
    await replyFeedback(feedback.value.id, replyContent.value.trim())
    message.success(t('feedback.replySuccess'))
    showReply.value = false
    await load()
  } finally {
    replySaving.value = false
  }
}

function confirmClose() {
  if (!feedback.value) return
  dialog.warning({
    title: t('feedback.closeTitle'),
    content: t('feedback.closeConfirm'),
    positiveText: t('feedback.close'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await closeFeedback(feedback.value!.id)
      message.success(t('feedback.closedSuccess'))
      await load()
    },
  })
}

async function doReopen() {
  if (!feedback.value) return
  await reopenFeedback(feedback.value.id)
  message.success(t('feedback.reopened'))
  await load()
}

function goUser() {
  if (!feedback.value?.userId) return
  router.push(`/admin/users/${feedback.value.userId}`)
}

watch(feedbackId, () => {
  void load()
})

onMounted(async () => {
  await Promise.all([load(), loadAssigneeOptions()])
})
</script>

<template>
  <div class="page">
    <NSpin :show="loading">
      <div v-if="notFound" class="page-shell empty-state">
        <p>{{ t('feedback.notFound') }}</p>
        <NButton @click="router.push('/admin/feedback')">{{ t('feedback.backToList') }}</NButton>
      </div>

      <div v-else-if="feedback" class="page-shell">
        <NSpace class="page-toolbar" justify="end">
          <NButton @click="router.push('/admin/feedback')">{{ t('feedback.backToList') }}</NButton>
          <NButton v-if="auth.hasPermission('admin:feedback:assign')" @click="openAssign">
            {{ t('feedback.assign') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:feedback:reply') && feedback.status !== 'closed'"
            type="primary"
            @click="openReply"
          >
            {{ t('feedback.reply') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:feedback:close') && feedback.status !== 'closed'"
            type="warning"
            secondary
            @click="confirmClose"
          >
            {{ t('feedback.close') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:feedback:reply') && feedback.status === 'closed'"
            @click="doReopen"
          >
            {{ t('feedback.reopen') }}
          </NButton>
        </NSpace>

        <NDescriptions label-placement="left" :column="2" bordered>
          <NDescriptionsItem label="ID">{{ feedback.id }}</NDescriptionsItem>
          <NDescriptionsItem :label="t('common.status')">
            <NTag v-if="feedback.overdue" type="error" size="small">{{
              t('feedback.overdue')
            }}</NTag>
            <NTag v-else-if="feedback.status === 'pending'" type="warning" size="small">
              {{ t('feedback.pending') }}
            </NTag>
            <NTag v-else-if="feedback.status === 'replied'" type="success" size="small">
              {{ t('feedback.replied') }}
            </NTag>
            <NTag v-else-if="feedback.status === 'closed'" size="small">
              {{ t('feedback.closed') }}
            </NTag>
            <span v-else>{{ displayOrNone(feedback.status) }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem :label="t('feedback.user')">
            <NSpace v-if="feedback.username && feedback.userId" align="center" :size="8">
              <span>{{ feedback.username }}</span>
              <NButton
                v-if="auth.hasPermission('admin:user:view')"
                text
                type="primary"
                size="tiny"
                @click="goUser"
              >
                {{ t('feedback.viewUser') }}
              </NButton>
            </NSpace>
            <span v-else>{{ displayOrNone(feedback.username) }}</span>
          </NDescriptionsItem>
          <NDescriptionsItem :label="t('feedback.type')">{{
            typeLabel(feedback.type)
          }}</NDescriptionsItem>
          <NDescriptionsItem :label="t('feedback.contact')" :span="2">
            {{ displayOrNone(feedback.contact) }}
          </NDescriptionsItem>
          <NDescriptionsItem :label="t('feedback.assignee')">
            {{ feedback.assigneeName || t('feedback.unassigned') }}
          </NDescriptionsItem>
          <NDescriptionsItem :label="t('feedback.assignedAt')">
            {{ formatTime(feedback.assignedAt) }}
          </NDescriptionsItem>
          <NDescriptionsItem :label="t('common.createTime')">
            {{ formatTime(feedback.createTime) }}
          </NDescriptionsItem>
        </NDescriptions>

        <section class="thread-section">
          <h3 class="thread-title">{{ t('feedback.conversation') }}</h3>
          <div class="thread-list">
            <article class="thread-item thread-item--user">
              <div class="thread-meta">
                <span class="thread-sender">{{ feedback.username || t('feedback.user') }}</span>
                <span class="thread-time">{{ formatTime(feedback.createTime) }}</span>
              </div>
              <div class="thread-content">{{ feedback.content || t('common.none') }}</div>
            </article>
            <article
              v-for="item in feedback.replies || []"
              :key="item.id"
              class="thread-item"
              :class="item.senderType === 'admin' ? 'thread-item--admin' : 'thread-item--user'"
            >
              <div class="thread-meta">
                <span class="thread-sender">
                  {{
                    item.senderName ||
                    (item.senderType === 'admin'
                      ? t('feedback.senderAdmin')
                      : t('feedback.senderUser'))
                  }}
                </span>
                <span class="thread-time">{{ formatTime(item.createTime) }}</span>
              </div>
              <div class="thread-content">{{ item.content }}</div>
            </article>
            <p v-if="!feedback.replies?.length" class="thread-empty">
              {{ t('feedback.noReplyYet') }}
            </p>
          </div>
        </section>
      </div>
    </NSpin>

    <AdminFormShell
      v-model:show="showReply"
      
      :title="t('feedback.replyTitle')"
      
     :width="520">
      <p class="reply-quote">{{ feedback?.content }}</p>
      <NInput
        v-model:value="replyContent"
        type="textarea"
        :rows="4"
        :placeholder="t('feedback.replyPlaceholder')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showReply = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="replySaving" @click="submitReply">
            {{ t('common.submit') }}
          </NButton>
        </NSpace>
      </template>
    </AdminFormShell>

    <AdminFormShell
      v-model:show="showAssign"
      
      :title="t('feedback.assignTitle')"
      
     :width="480">
      <p class="reply-quote">{{ feedback?.content }}</p>
      <NSelect
        v-model:value="assigneeId"
        clearable
        filterable
        :options="assigneeOptions"
        :placeholder="t('feedback.assigneePlaceholder')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showAssign = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="assignSaving" @click="submitAssign">
            {{ t('common.submit') }}
          </NButton>
        </NSpace>
      </template>
    </AdminFormShell>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 48px 24px;
  text-align: center;
  color: var(--lx-text-2);
}
.content-block {
  white-space: pre-wrap;
  line-height: 1.6;
  word-break: break-word;
}
.reply-block {
  color: var(--lx-text-2);
}
.reply-quote {
  color: var(--lx-text-2);
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
}
.thread-section {
  margin-top: 20px;
}
.thread-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}
.thread-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.thread-item {
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  padding: 12px 14px;
  background: var(--lx-card);
}
.thread-item--admin {
  border-color: rgba(24, 160, 88, 0.35);
  background: rgba(24, 160, 88, 0.06);
}
.thread-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--lx-text-2);
  font-size: 12px;
}
.thread-sender {
  font-weight: 600;
  color: var(--lx-text-1);
}
.thread-content {
  white-space: pre-wrap;
  line-height: 1.6;
  word-break: break-word;
}
.thread-empty {
  margin: 0;
  color: var(--lx-text-2);
  font-size: 13px;
}
</style>
