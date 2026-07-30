<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { NButton, NDataTable, NInput, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { h } from 'vue'
import { listPermissions, type AdminPermission } from '@/api/menus'

const loading = ref(false)
const items = ref<AdminPermission[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const columns: DataTableColumns<AdminPermission> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '权限编码', key: 'permissionCode', ellipsis: { tooltip: true } },
  { title: '权限名称', key: 'permissionName' },
  { title: '资源类型', key: 'resourceType', width: 120 },
  { title: '资源路径', key: 'resourcePath', ellipsis: { tooltip: true } },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(NTag, { type: row.status === 1 ? 'success' : 'default', size: 'small' }, () =>
        row.status === 1 ? '启用' : '停用',
      ),
  },
]

async function load() {
  loading.value = true
  try {
    const data = await listPermissions({
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
      <h1 class="page-title">权限管理</h1>
    </div>
    <div class="page-card">
      <NSpace style="margin-bottom: 16px">
        <NInput
          v-model:value="query.keyword"
          clearable
          placeholder="搜索权限编码/名称"
          style="width: 240px"
          @keyup.enter="() => { query.page = 1; load() }"
        />
        <NButton type="primary" @click="() => { query.page = 1; load() }">查询</NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
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
