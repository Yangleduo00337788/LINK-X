<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
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
  approveReview,
  listReviews,
  rejectReview,
  type ReviewItem,
} from '@/api/reviews'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<ReviewItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '', sourceType: '' })
const knownIds = ref<Set<string>>(new Set())
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
const POLL_MS = 5000

const showResolve = ref(false)
const resolveTarget = ref<ReviewItem | null>(null)
const resolveAction = ref<'approve' | 'reject'>('approve')
const resolution = ref('')
const resolveSaving = ref(false)

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('review.pending'), value: 'pending' },
    { label: t('review.approved'), value: 'approved' },
    { label: t('review.rejected'), value: 'rejected' },
  ]
})

const sourceOptions = computed(() => {
  void locale.value
  return [
    { label: t('review.allSources'), value: '' },
    { label: t('review.sourceReport'), value: 'report' },
    { label: t('review.sourceSensitive'), value: 'sensitive' },
    { label: t('review.sourceManual'), value: 'manual' },
  ]
})

function statusTag(status?: string) {
  const map: Record<string, 'warning' | 'success' | 'error' | 'default'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'error',
  }
  const label: Record<string, string> = {
    pending: t('review.pending'),
    approved: t('review.approved'),
    rejected: t('review.rejected'),
  }
  return h(NTag, { type: map[status || ''] || 'default', size: 'small' }, () => label[status || ''] || status || '-')
}

function sourceLabel(source?: string) {
  const map: Record<string, string> = {
    report: t('review.sourceReport'),
    sensitive: t('review.sourceSensitive'),
    manual: t('review.sourceManual'),
  }
  return map[source || ''] || source || '-'
}

const columns = computed<DataTableColumns<ReviewItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('review.source'),
      key: 'sourceType',
      width: 100,
      render: (row) => sourceLabel(row.sourceType),
    },
    { title: t('review.title'), key: 'title', width: 160, ellipsis: { tooltip: true } },
    { title: t('review.content'), key: 'contentSnapshot', ellipsis: { tooltip: true } },
    { title: t('review.reporter'), key: 'reporterUsername', width: 110 },
    { title: t('review.target'), key: 'targetId', width: 120, ellipsis: { tooltip: true } },
    { title: t('common.status'), key: 'status', width: 100, render: (row) => statusTag(row.status) },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'tiny', onClick: () => showDetail(row) }, () => t('common.detail')),
          row.status === 'pending' && auth.hasPermission('admin:review:approve')
            ? h(NButton, { size: 'tiny', type: 'success', secondary: true, onClick: () => openResolve(row, 'approve') }, () =>
                t('review.approve'),
              )
            : null,
          row.status === 'pending' && auth.hasPermission('admin:review:reject')
            ? h(NButton, { size: 'tiny', type: 'error', secondary: true, onClick: () => openResolve(row, 'reject') }, () =>
                t('review.reject'),
              )
            : null,
        ]),
    },
  ]
})

function openResolve(row: ReviewItem, action: 'approve' | 'reject') {
  resolveTarget.value = row
  resolveAction.value = action
  resolution.value = ''
  showResolve.value = true
}

function stripEvidenceText(content?: string) {
  if (!content) return '-'
  // JS 不支持 (?m) 内联标志，须用 m 标志
  return content
    .replace(/^证据图片:\s*$/gm, '')
    .replace(/^\d+\.\s*[\w./-]+\.(?:png|jpe?g|gif|webp|bmp)\s*$/gim, '')
    .replace(/^证据图片:\s*无\s*$/gm, `${t('review.evidence')}: ${t('review.evidenceNone')}`)
    .replace(/\n{3,}/g, '\n\n')
    .trim() || '-'
}

function showDetail(row: ReviewItem) {
  const urls = row.evidenceUrls || []
  dialog.info({
    title: row.title || t('review.detailTitle'),
    content: () =>
      h(
        'div',
        { style: 'line-height: 1.6; max-height: 420px; overflow: auto;' },
        [
          h('div', { style: 'white-space: pre-wrap; margin-bottom: 12px;' }, stripEvidenceText(row.contentSnapshot)),
          urls.length
            ? h('div', { style: 'margin-bottom: 8px; font-weight: 600;' }, t('review.evidence'))
            : null,
          urls.length
            ? h(
                'div',
                { style: 'display: flex; flex-direction: column; gap: 10px;' },
                urls.map((src) =>
                  h('a', { href: src, target: '_blank', rel: 'noopener noreferrer' }, [
                    h('img', {
                      src,
                      alt: '',
                      style:
                        'max-width: 100%; max-height: 360px; width: auto; height: auto; object-fit: contain; border-radius: 8px; border: 1px solid var(--lx-border, #e5e5e5); display: block;',
                    }),
                  ]),
                ),
              )
            : null,
          row.resolution
            ? h(
                'div',
                { style: 'white-space: pre-wrap; margin-top: 12px;' },
                `${t('review.resolution')}: ${row.resolution}`,
              )
            : null,
        ],
      ),
    positiveText: t('common.confirm'),
  })
}

async function submitResolve() {
  if (!resolveTarget.value) return
  resolveSaving.value = true
  try {
    const note = resolution.value.trim() || undefined
    if (resolveAction.value === 'approve') {
      await approveReview(resolveTarget.value.id, note)
      message.success(t('review.approveSuccess'))
    } else {
      await rejectReview(resolveTarget.value.id, note)
      message.success(t('review.rejectSuccess'))
    }
    showResolve.value = false
    await load()
  } finally {
    resolveSaving.value = false
  }
}

async function load(opts?: { silent?: boolean; announceNew?: boolean }) {
  const silent = !!opts?.silent
  if (!silent) loading.value = true
  try {
    const data = await listReviews({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      reviewStatus: query.status || undefined,
      sourceType: query.sourceType || undefined,
    })
    const next = data.items || []
    if (opts?.announceNew && knownIds.value.size > 0) {
      const fresh = next.filter((row) => !knownIds.value.has(String(row.id)))
      if (fresh.length > 0 && query.page === 1) {
        message.info(t('review.newArrived', { n: fresh.length }))
      }
    }
    items.value = next
    total.value = data.total || 0
    knownIds.value = new Set(next.map((row) => String(row.id)))
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void load({ silent: true, announceNew: true })
  }
}

onMounted(() => {
  void load()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    if (showResolve.value) return
    void load({ silent: true, announceNew: true })
  }, POLL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar">
        <SearchAutoComplete
          v-model="query.keyword"
          :placeholder="t('review.searchPlaceholder')"
          width="220px"
          @search="search"
        />
        <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
        <NSelect v-model:value="query.sourceType" :options="sourceOptions" style="width: 140px" />
        <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="1200"
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

    <NModal
      v-model:show="showResolve"
      preset="card"
      :title="resolveAction === 'approve' ? t('review.approveTitle') : t('review.rejectTitle')"
      style="width: 520px"
    >
      <p class="quote">{{ stripEvidenceText(resolveTarget?.contentSnapshot) }}</p>
      <div v-if="resolveTarget?.evidenceUrls?.length" class="evidence-block">
        <div class="evidence-label">{{ t('review.evidence') }}</div>
        <div class="evidence-grid">
          <a
            v-for="src in resolveTarget.evidenceUrls"
            :key="src"
            :href="src"
            target="_blank"
            rel="noopener noreferrer"
          >
            <img :src="src" alt="" />
          </a>
        </div>
      </div>
      <NInput
        v-model:value="resolution"
        type="textarea"
        :rows="3"
        :placeholder="t('review.resolutionPlaceholder')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showResolve = false">{{ t('common.cancel') }}</NButton>
          <NButton
            :type="resolveAction === 'approve' ? 'primary' : 'error'"
            :loading="resolveSaving"
            @click="submitResolve"
          >
            {{ resolveAction === 'approve' ? t('review.approve') : t('review.reject') }}
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.quote {
  color: var(--lx-text-2);
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  max-height: 180px;
  overflow: auto;
}
.evidence-block {
  margin-bottom: 12px;
}
.evidence-label {
  font-weight: 600;
  margin-bottom: 8px;
}
.evidence-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.evidence-grid img {
  max-width: 100%;
  max-height: 280px;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid var(--lx-border, #e5e5e5);
  display: block;
}
</style>
