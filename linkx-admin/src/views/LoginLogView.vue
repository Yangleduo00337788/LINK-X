<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { exportLoginLogs, listLoginLogs, type LoginLog } from '@/api/logs'
import { formatIp, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const auth = useAuthStore()
const { t, locale } = useI18n()
const loading = ref(false)
const exporting = ref(false)
const items = ref<LoginLog[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '' as '' | 0 | 1,
  range: null as [number, number] | null,
})

const resultOptions = computed(() => {
  void locale.value
  return [
    { label: t('loginLog.allResults'), value: '' },
    { label: t('common.success'), value: 1 },
    { label: t('common.failed'), value: 0 },
  ]
})

const columns = computed<DataTableColumns<LoginLog>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('loginLog.userId'), key: 'userId', width: 100 },
    { title: t('loginLog.username'), key: 'username', width: 140 },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    {
      title: t('loginLog.region'),
      key: 'region',
      width: 180,
      ellipsis: { tooltip: true },
      render: (row) => row.region || '-',
    },
    { title: 'UA', key: 'userAgent', ellipsis: { tooltip: true } },
    {
      title: t('loginLog.result'),
      key: 'success',
      width: 90,
      render: (row) =>
        h(NTag, { type: row.success === 1 ? 'success' : 'error', size: 'small' }, () =>
          row.success === 1 ? t('common.success') : t('common.failed'),
        ),
    },
    { title: t('loginLog.reason'), key: 'reason', ellipsis: { tooltip: true } },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
  ]
})

function queryParams() {
  return {
    page: query.page,
    size: query.size,
    keyword: query.keyword || undefined,
    status: query.status === '' ? undefined : query.status,
    startTime: query.range?.[0],
    endTime: query.range?.[1],
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listLoginLogs(queryParams())
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
    await exportLoginLogs({
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
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
            :placeholder="t('loginLog.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.status"
            :options="resultOptions"
            style="width: 130px"
          />
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:login-log:export')"
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
  </div>
</template>
