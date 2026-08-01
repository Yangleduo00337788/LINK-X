<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  closeFeedback,
  exportFeedback,
  listFeedback,
  reopenFeedback,
  replyFeedback,
  type FeedbackItem,
} from '@/api/feedback'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const items = ref<FeedbackItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '' as string,
  range: null as [number, number] | null,
})

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

function statusTag(status?: string) {
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
  return h(NTag, { type: map[status || ''] || 'default', size: 'small' }, () => label[status || ''] || status || '-')
}

const columns = computed<DataTableColumns<FeedbackItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('feedback.user'), key: 'username', width: 120 },
    { title: t('feedback.type'), key: 'type', width: 100 },
    { title: t('feedback.content'), key: 'content', ellipsis: { tooltip: true } },
    { title: t('feedback.contact'), key: 'contact', width: 140, ellipsis: { tooltip: true } },
    { title: t('common.status'), key: 'status', width: 100, render: (row) => statusTag(row.status) },
    { title: t('common.time'), key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
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
                () => t('feedback.close'),
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
                () => t('feedback.reopen'),
              )
            : null,
        ]),
    },
  ]
})

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

async function load() {
  loading.value = true
  try {
    const data = await listFeedback({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      feedbackStatus: query.status || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
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
      feedbackStatus: query.status || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

onMounted(load)
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
          <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
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
        :scroll-x="1100"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p: number) => { query.page = p; load() },
          onUpdatePageSize: (s: number) => { query.size = s; query.page = 1; load() },
        }"
        remote
      />
    </div>

    <NModal v-model:show="showReply" preset="card" :title="t('feedback.replyTitle')" style="width: 520px">
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
          <NButton type="primary" :loading="replySaving" @click="submitReply">{{ t('common.submit') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.reply-quote {
  color: var(--lx-text-2);
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
}
</style>
