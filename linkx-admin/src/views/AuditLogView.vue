<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { NButton, NDataTable, NInput, NSpace, type DataTableColumns } from 'naive-ui'
import { listAuditLogs, type AuditLog } from '@/api/logs'
import { formatIp, formatTime } from '@/utils/format'

const loading = ref(false)
const items = ref<AuditLog[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const columns: DataTableColumns<AuditLog> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '操作类型', key: 'operationType', width: 140 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '操作人', key: 'username', width: 120 },
  { title: '目标用户', key: 'targetUsername', width: 120 },
  {
    title: 'IP',
    key: 'ip',
    width: 140,
    render: (row) => formatIp(row.ip),
  },
  { title: '状态', key: 'status', width: 90 },
  {
    title: '时间',
    key: 'createTime',
    width: 170,
    render: (row) => formatTime(row.createTime),
  },
]

async function load() {
  loading.value = true
  try {
    const data = await listAuditLogs({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">操作日志</h1>
    </div>
    <div class="page-card">
      <NSpace style="margin-bottom: 16px">
        <NInput
          v-model:value="query.keyword"
          clearable
          placeholder="搜索操作类型/用户"
          style="width: 240px"
          @keyup.enter="() => { query.page = 1; load() }"
        />
        <NButton type="primary" @click="() => { query.page = 1; load() }">查询</NButton>
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
          onUpdatePage: (p: number) => { query.page = p; load() },
          onUpdatePageSize: (s: number) => { query.size = s; query.page = 1; load() },
        }"
        remote
      />
    </div>
  </div>
</template>
