<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NDataTable, NInput, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { listLoginLogs, type LoginLog } from '@/api/logs'
import { formatIp, formatTime } from '@/utils/format'

const loading = ref(false)
const items = ref<LoginLog[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const columns: DataTableColumns<LoginLog> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '用户 ID', key: 'userId', width: 100 },
  { title: '用户名', key: 'username', width: 140 },
  {
    title: 'IP',
    key: 'ip',
    width: 140,
    render: (row) => formatIp(row.ip),
  },
  { title: 'UA', key: 'userAgent', ellipsis: { tooltip: true } },
  {
    title: '结果',
    key: 'success',
    width: 90,
    render: (row) =>
      h(NTag, { type: row.success === 1 ? 'success' : 'error', size: 'small' }, () =>
        row.success === 1 ? '成功' : '失败',
      ),
  },
  { title: '原因', key: 'reason', ellipsis: { tooltip: true } },
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
    const data = await listLoginLogs({
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
      <h1 class="page-title">登录日志</h1>
    </div>
    <div class="page-card">
      <NSpace style="margin-bottom: 16px">
        <NInput
          v-model:value="query.keyword"
          clearable
          placeholder="搜索用户名/IP"
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
