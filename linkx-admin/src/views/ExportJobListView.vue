<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  downloadExportJob,
  listExportJobs,
  type ExportJob,
  type ExportJobStatus,
  type ExportModule,
} from '@/api/exportJobs'
import { formatTime } from '@/utils/format'

const { t, locale } = useI18n()
const message = useMessage()
const loading = ref(false)
const downloadingId = ref<string | number | null>(null)
const items = ref<ExportJob[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
})

const moduleLabelKeys: Record<string, string> = {
  users: 'exportJob.moduleUsers',
  devices: 'exportJob.moduleDevices',
  blacklist: 'exportJob.moduleBlacklist',
  'risk-events': 'exportJob.moduleRiskEvents',
  reviews: 'exportJob.moduleReviews',
  feedback: 'exportJob.moduleFeedback',
  'audit-logs': 'exportJob.moduleAuditLogs',
  'login-logs': 'exportJob.moduleLoginLogs',
  statistics: 'exportJob.moduleStatistics',
}

function moduleLabel(code?: string) {
  if (!code) return '-'
  const key = moduleLabelKeys[code]
  return key ? t(key) : code
}

function statusMeta(status?: ExportJobStatus) {
  switch (status) {
    case 'PENDING':
      return { type: 'warning' as const, label: t('exportJob.statusPending') }
    case 'RUNNING':
      return { type: 'info' as const, label: t('exportJob.statusRunning') }
    case 'SUCCESS':
      return { type: 'success' as const, label: t('exportJob.statusSuccess') }
    case 'FAILED':
      return { type: 'error' as const, label: t('exportJob.statusFailed') }
    case 'EXPIRED':
      return { type: 'default' as const, label: t('exportJob.statusExpired') }
    default:
      return { type: 'default' as const, label: status || '-' }
  }
}

const columns = computed<DataTableColumns<ExportJob>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    {
      title: t('exportJob.module'),
      key: 'module',
      width: 120,
      render: (row) => moduleLabel(row.module),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) => {
        const meta = statusMeta(row.status)
        return h(NTag, { size: 'small', type: meta.type, bordered: false }, () => meta.label)
      },
    },
    { title: t('exportJob.rowCount'), key: 'rowCount', width: 90 },
    {
      title: t('exportJob.fileName'),
      key: 'fileName',
      minWidth: 180,
      ellipsis: { tooltip: true },
    },
    {
      title: t('exportJob.errorMessage'),
      key: 'errorMessage',
      minWidth: 160,
      ellipsis: { tooltip: true },
      render: (row) => row.errorMessage || '-',
    },
    {
      title: t('exportJob.expireAt'),
      key: 'expireAt',
      width: 170,
      render: (row) => (row.expireAt ? formatTime(row.expireAt) : '-'),
    },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (row) =>
        h(
          NButton,
          {
            size: 'small',
            tertiary: true,
            loading: downloadingId.value === row.id,
            disabled: row.status !== 'SUCCESS',
            onClick: () => downloadRow(row),
          },
          () => t('common.download')
        ),
    },
  ]
})

async function load() {
  loading.value = true
  try {
    const data = await listExportJobs({
      page: query.page,
      size: query.size,
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function downloadRow(row: ExportJob) {
  downloadingId.value = row.id
  try {
    await downloadExportJob(row.id, row.fileName || `${row.module as ExportModule}.csv`)
    message.success(t('exportJob.downloadOk'))
  } catch (e) {
    message.error(e instanceof Error ? e.message : t('common.requestFailed'))
  } finally {
    downloadingId.value = null
  }
}

function onPageChange(page: number) {
  query.page = page
  void load()
}

function onPageSizeChange(size: number) {
  query.size = size
  query.page = 1
  void load()
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <p class="hint">{{ t('exportJob.listHint') }}</p>
    <NSpace class="toolbar" justify="end">
      <NButton :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </NSpace>
    <NDataTable
      remote
      :loading="loading"
      :columns="columns"
      :data="items"
      :bordered="false"
      size="small"
      :scroll-x="1100"
      :pagination="{
        page: query.page,
        pageSize: query.size,
        itemCount: total,
        showSizePicker: true,
        pageSizes: [10, 20, 50],
        onUpdatePage: onPageChange,
        onUpdatePageSize: onPageSizeChange,
      }"
    />
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--lx-text-3);
  font-size: 13px;
}
.toolbar {
  margin-bottom: 12px;
}
</style>
