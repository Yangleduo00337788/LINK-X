<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NGrid,
  NGridItem,
  NInput,
  NSelect,
  NSpace,
  NStatistic,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  exportAbnormalAccess,
  getAbnormalAccessSummary,
  listAbnormalAccess,
  type AbnormalAccessItem,
  type AbnormalAccessSummary,
} from '@/api/abnormalAccess'
import { displayCount, displayOrNone, formatIp, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const auth = useAuthStore()
const router = useRouter()
const { t, locale } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const items = ref<AbnormalAccessItem[]>([])
const total = ref(0)
const summary = ref<AbnormalAccessSummary | null>(null)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  source: '' as string,
  ip: '',
  range: null as [number, number] | null,
})

const sourceOptions = computed(() => {
  void locale.value
  return [
    { label: t('abnormalAccess.sourceAll'), value: '' },
    { label: t('abnormalAccess.sourceLoginFail'), value: 'login_fail' },
    { label: t('abnormalAccess.sourceRateLimit'), value: 'rate_limit' },
    { label: t('abnormalAccess.sourceRiskEvent'), value: 'risk_event' },
  ]
})

function sourceTag(row: AbnormalAccessItem) {
  const map: Record<string, { type: 'error' | 'warning' | 'info' | 'default'; label: string }> = {
    login_fail: { type: 'error', label: t('abnormalAccess.sourceLoginFail') },
    rate_limit: { type: 'warning', label: t('abnormalAccess.sourceRateLimit') },
    risk_event: { type: 'info', label: t('abnormalAccess.sourceRiskEvent') },
  }
  const meta = map[row.source || ''] || { type: 'default', label: row.source || '-' }
  return h(NTag, { type: meta.type, size: 'small' }, () => meta.label)
}

function metricText(row: AbnormalAccessItem) {
  if (row.source === 'rate_limit') {
    const count = displayCount(row.hitCount)
    if (row.ttlSeconds != null && row.ttlSeconds > 0) {
      return `${count} (${row.ttlSeconds}s)`
    }
    return count
  }
  if (row.source === 'login_fail') {
    return t('abnormalAccess.metricLoginFail')
  }
  if (row.source === 'risk_event') {
    const status = row.status
    if (status === 'pending') return t('risk.pending')
    if (status === 'handled') return t('risk.handled')
    if (status === 'ignored') return t('risk.ignored')
    return displayOrNone(status)
  }
  return displayOrNone(null)
}

const columns = computed<DataTableColumns<AbnormalAccessItem>>(() => {
  void locale.value
  return [
    {
      title: t('abnormalAccess.source'),
      key: 'source',
      width: 110,
      render: (row) => sourceTag(row),
    },
    { title: t('abnormalAccess.category'), key: 'category', width: 120, ellipsis: { tooltip: true } },
    { title: t('abnormalAccess.titleCol'), key: 'title', width: 160, ellipsis: { tooltip: true } },
    { title: t('abnormalAccess.detail'), key: 'detail', ellipsis: { tooltip: true } },
    { title: t('loginLog.username'), key: 'username', width: 120, render: (row) => displayOrNone(row.username || row.identity) },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    {
      title: t('loginLog.region'),
      key: 'region',
      width: 120,
      render: (row) => displayOrNone(row.region),
    },
    {
      title: t('abnormalAccess.metric'),
      key: 'metric',
      width: 110,
      render: (row) => metricText(row),
    },
    {
      title: t('common.time'),
      key: 'occurredAt',
      width: 170,
      render: (row) => formatTime(row.occurredAt),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 140,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          row.source === 'login_fail' && auth.hasPermission('admin:login-log:list')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  onClick: () =>
                    router.push({
                      path: '/admin/login-logs',
                      query: { keyword: row.username || row.ip || '' },
                    }),
                },
                () => t('abnormalAccess.viewLoginLog')
              )
            : null,
          row.source === 'risk_event' && row.sourceId
            ? h(
                NButton,
                {
                  size: 'tiny',
                  onClick: () => router.push({ path: '/admin/risk-events', query: { highlight: row.sourceId } }),
                },
                () => t('abnormalAccess.viewRisk')
              )
            : null,
          row.source === 'rate_limit' && row.ip && auth.hasPermission('admin:rate-limit:unblock')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  onClick: () => router.push('/admin/rate-limits'),
                },
                () => t('abnormalAccess.viewRateLimit')
              )
            : null,
        ]),
    },
  ]
})

async function loadSummary() {
  summary.value = await getAbnormalAccessSummary()
}

async function load() {
  loading.value = true
  try {
    const data = await listAbnormalAccess({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      source: query.source || undefined,
      ip: query.ip || undefined,
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
    await exportAbnormalAccess({
      keyword: query.keyword || undefined,
      source: query.source || undefined,
      ip: query.ip || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadSummary(), load()])
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NGrid v-if="summary" :cols="3" :x-gap="16" class="summary-grid">
        <NGridItem>
          <NCard size="small">
            <NStatistic :label="t('abnormalAccess.loginFail24h')" :value="summary.loginFail24h" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard size="small">
            <NStatistic :label="t('abnormalAccess.rateLimitActive')" :value="summary.rateLimitActive" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard size="small">
            <NStatistic :label="t('abnormalAccess.riskEventPending')" :value="summary.riskEventPending" />
          </NCard>
        </NGridItem>
      </NGrid>

      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('abnormalAccess.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.source"
            :options="sourceOptions"
            style="width: 150px"
            @update:value="search"
          />
          <NInput v-model:value="query.ip" :placeholder="t('abnormalAccess.ipPlaceholder')" style="width: 160px" />
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:abnormal-access:export')"
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
        :scroll-x="1200"
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
  </div>
</template>

<style scoped>
.summary-grid {
  margin-bottom: 16px;
}
</style>
