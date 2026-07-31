<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NDataTable, NDatePicker, NSpace, useMessage, type DataTableColumns } from 'naive-ui'
import { exportAuditLogs, listAuditLogs, type AuditLog } from '@/api/logs'
import { formatIp, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const auth = useAuthStore()
const { t, locale } = useI18n()
const loading = ref(false)
const exporting = ref(false)
const items = ref<AuditLog[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  range: null as [number, number] | null,
})

const columns = computed<DataTableColumns<AuditLog>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('audit.operationType'), key: 'operationType', width: 140 },
    { title: t('common.description'), key: 'description', ellipsis: { tooltip: true } },
    { title: t('audit.operator'), key: 'username', width: 120 },
    { title: t('audit.targetUser'), key: 'targetUsername', width: 120 },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    { title: t('common.status'), key: 'status', width: 90 },
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
    startTime: query.range?.[0],
    endTime: query.range?.[1],
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listAuditLogs(queryParams())
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
    await exportAuditLogs({
      keyword: query.keyword || undefined,
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
            :placeholder="t('audit.searchPlaceholder')"
            width="220px"
            @search="search"
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
          v-if="auth.hasPermission('admin:audit:export')"
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
