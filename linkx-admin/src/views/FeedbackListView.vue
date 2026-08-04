<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type SelectOption,
} from 'naive-ui'
import {
  assignFeedback,
  closeFeedback,
  exportFeedback,
  listFeedback,
  reopenFeedback,
  replyFeedback,
  type FeedbackItem,
} from '@/api/feedback'
import { listUsers } from '@/api/users'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { notifyPendingTask } from '@/utils/adminNotify'
import { onAdminRealtimeEvent } from '@/api/realtime'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const assigneeOptions = ref<SelectOption[]>([])

const loading = ref(false)
const exporting = ref(false)
const items = ref<FeedbackItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '' as string,
  overdueOnly: false,
  escalatedOnly: false,
  unassignedOnly: false,
  mineOnly: false,
  range: null as [number, number] | null,
})

const showAssign = ref(false)
const assignTarget = ref<FeedbackItem | null>(null)
const assigneeId = ref<string | null>(null)
const assignSaving = ref(false)

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('feedback.pending'), value: 'pending' },
    { label: t('feedback.replied'), value: 'replied' },
    { label: t('feedback.closed'), value: 'closed' },
  ]
})

const showReply = ref(false)
const replyTarget = ref<FeedbackItem | null>(null)
const replyContent = ref('')
const replySaving = ref(false)
const knownIds = ref<Set<string>>(new Set())
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
let offRealtime: (() => void) | null = null
const POLL_MS = 12000

function statusTag(row: FeedbackItem) {
  const status = row.status
  const tags = []
  if (row.escalated) {
    const label =
      row.escalationCount && row.escalationCount > 1
        ? t('feedback.escalatedCount', { count: row.escalationCount })
        : t('feedback.escalated')
    tags.push(h(NTag, { type: 'error', size: 'small' }, () => label))
  } else if (row.overdue) {
    tags.push(h(NTag, { type: 'error', size: 'small' }, () => t('feedback.overdue')))
  }
  const map: Record<string, 'warning' | 'success' | 'default'> = {
    pending: 'warning',
    replied: 'success',
    closed: 'default',
  }
  const label: Record<string, string> = {
    pending: t('feedback.pending'),
    replied: t('feedback.replied'),
    closed: t('feedback.closed'),
  }
  tags.push(
    h(
      NTag,
      { type: map[status || ''] || 'default', size: 'small' },
      () => label[status || ''] || status || '-'
    )
  )
  if (tags.length === 1) return tags[0]
  return h(NSpace, { size: 4, align: 'center' }, () => tags)
}

const columns = computed<DataTableColumns<FeedbackItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('feedback.user'), key: 'username', width: 120 },
    { title: t('feedback.type'), key: 'type', width: 100 },
    { title: t('feedback.content'), key: 'content', ellipsis: { tooltip: true } },
    { title: t('feedback.contact'), key: 'contact', width: 140, ellipsis: { tooltip: true } },
    {
      title: t('feedback.assignee'),
      key: 'assigneeName',
      width: 110,
      render: (row) => row.assigneeName || t('feedback.unassigned'),
    },
    { title: t('common.status'), key: 'status', width: 100, render: (row) => statusTag(row) },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 320,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          h(
            NButton,
            {
              size: 'tiny',
              type: 'primary',
              secondary: true,
              onClick: () => router.push(`/admin/feedback/${row.id}`),
            },
            () => t('feedback.viewDetail')
          ),
          auth.hasPermission('admin:feedback:assign')
            ? h(NButton, { size: 'tiny', onClick: () => openAssign(row) }, () =>
                t('feedback.assign')
              )
            : null,
          auth.hasPermission('admin:feedback:reply') && row.status !== 'closed'
            ? h(NButton, { size: 'tiny', onClick: () => openReply(row) }, () => t('feedback.reply'))
            : null,
          auth.hasPermission('admin:feedback:close') && row.status !== 'closed'
            ? h(
                NButton,
                {
                  size: 'tiny',
                  onClick: () =>
                    dialog.warning({
                      title: t('feedback.closeTitle'),
                      content: t('feedback.closeConfirm'),
                      positiveText: t('feedback.close'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await closeFeedback(row.id)
                        message.success(t('feedback.closedSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('feedback.close')
              )
            : null,
          auth.hasPermission('admin:feedback:reply') && row.status === 'closed'
            ? h(
                NButton,
                {
                  size: 'tiny',
                  onClick: async () => {
                    await reopenFeedback(row.id)
                    message.success(t('feedback.reopened'))
                    await load()
                  },
                },
                () => t('feedback.reopen')
              )
            : null,
        ]),
    },
  ]
})

function openAssign(row: FeedbackItem) {
  assignTarget.value = row
  assigneeId.value = row.assigneeId || null
  showAssign.value = true
}

async function submitAssign() {
  if (!assignTarget.value) return
  assignSaving.value = true
  try {
    await assignFeedback(assignTarget.value.id, assigneeId.value)
    message.success(t('feedback.assignSuccess'))
    showAssign.value = false
    await load()
  } finally {
    assignSaving.value = false
  }
}

function openReply(row: FeedbackItem) {
  replyTarget.value = row
  replyContent.value = ''
  showReply.value = true
}

async function submitReply() {
  if (!replyTarget.value || !replyContent.value.trim()) {
    message.warning(t('feedback.replyRequired'))
    return
  }
  replySaving.value = true
  try {
    await replyFeedback(replyTarget.value.id, replyContent.value.trim())
    message.success(t('feedback.replySuccess'))
    showReply.value = false
    await load()
  } finally {
    replySaving.value = false
  }
}

async function load(opts?: { silent?: boolean; announceNew?: boolean }) {
  if (!opts?.silent) loading.value = true
  try {
    const data = await listFeedback({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      feedbackStatus: query.overdueOnly ? 'pending' : query.status || undefined,
      overdueOnly: query.overdueOnly || undefined,
      escalatedOnly: query.escalatedOnly || undefined,
      unassignedOnly: query.unassignedOnly || undefined,
      mineOnly: query.mineOnly || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    const next = data.items || []
    const pendingFilter =
      query.overdueOnly || !query.status || query.status === 'pending'
    if (opts?.announceNew && knownIds.value.size > 0 && pendingFilter) {
      const fresh = next.filter((row) => !knownIds.value.has(String(row.id)))
      if (fresh.length > 0 && query.page === 1) {
        message.info(t('feedback.createdRealtime'))
        notifyPendingTask(t, locale.value)
      }
    }
    items.value = next
    total.value = data.total || 0
    knownIds.value = new Set(next.map((row) => String(row.id)))
  } finally {
    if (!opts?.silent) loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

async function doExport() {
  exporting.value = true
  try {
    await exportFeedback({
      keyword: query.keyword || undefined,
      feedbackStatus: query.overdueOnly ? 'pending' : query.status || undefined,
      overdueOnly: query.overdueOnly || undefined,
      escalatedOnly: query.escalatedOnly || undefined,
      unassignedOnly: query.unassignedOnly || undefined,
      mineOnly: query.mineOnly || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void load({ silent: true, announceNew: true })
  }
}

onMounted(async () => {
  const overdue = route.query.overdueOnly
  if (overdue === '1' || overdue === 'true') {
    query.overdueOnly = true
    query.status = 'pending'
  }
  const data = await listUsers({ page: 1, size: 100 })
  assigneeOptions.value = (data.items || []).map((u) => ({
    label: u.nickname || u.username || u.id,
    value: u.id,
  }))
  void load()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    if (showReply.value || showAssign.value) return
    void load({ silent: true, announceNew: true })
  }, POLL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
  offRealtime = onAdminRealtimeEvent((evt) => {
    if (evt?.type === 'feedback_created' || evt?.type === 'feedback_escalated') {
      void load({ silent: true, announceNew: false })
    }
  })
})

onUnmounted(() => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
  offRealtime?.()
  offRealtime = null
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('feedback.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.status"
            :options="statusOptions"
            :disabled="query.overdueOnly"
            style="width: 140px"
          />
          <NSpace align="center">
            <span class="muted">{{ t('feedback.overdueOnly') }}</span>
            <NSwitch v-model:value="query.overdueOnly" @update:value="search" />
          </NSpace>
          <NSpace align="center">
            <span class="muted">{{ t('feedback.escalatedOnly') }}</span>
            <NSwitch v-model:value="query.escalatedOnly" @update:value="search" />
          </NSpace>
          <NSpace align="center">
            <span class="muted">{{ t('feedback.unassignedOnly') }}</span>
            <NSwitch v-model:value="query.unassignedOnly" @update:value="search" />
          </NSpace>
          <NSpace align="center">
            <span class="muted">{{ t('feedback.mineOnly') }}</span>
            <NSwitch v-model:value="query.mineOnly" @update:value="search" />
          </NSpace>
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:feedback-dispatch-rule:list')"
          @click="router.push('/admin/feedback-dispatch-rules')"
        >
          {{ t('feedback.dispatchRules') }}
        </NButton>
        <NButton
          v-if="auth.hasPermission('admin:feedback:export')"
          :loading="exporting"
          @click="doExport"
        >
          {{ t('common.export') }}
        </NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="1240"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p: number) => {
            query.page = p
            load()
          },
          onUpdatePageSize: (s: number) => {
            query.size = s
            query.page = 1
            load()
          },
        }"
        remote
      />
    </div>

    <AdminFormShell
      v-model:show="showReply"
      
      :title="t('feedback.replyTitle')"
      
     :width="520">
      <p class="reply-quote">{{ replyTarget?.content }}</p>
      <NInput
        v-model:value="replyContent"
        type="textarea"
        :rows="4"
        :placeholder="t('feedback.replyPlaceholder')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showReply = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="replySaving" @click="submitReply">{{
            t('common.submit')
          }}</NButton>
        </NSpace>
      </template>
    </AdminFormShell>

    <AdminFormShell
      v-model:show="showAssign"
      
      :title="t('feedback.assignTitle')"
      
     :width="480">
      <p class="reply-quote">{{ assignTarget?.content }}</p>
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
          <NButton type="primary" :loading="assignSaving" @click="submitAssign">{{
            t('common.submit')
          }}</NButton>
        </NSpace>
      </template>
    </AdminFormShell>
  </div>
</template>

<style scoped>
.muted {
  color: var(--lx-text-2);
  font-size: 13px;
}
.reply-quote {
  color: var(--lx-text-2);
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
}
</style>
