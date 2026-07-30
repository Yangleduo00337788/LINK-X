<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { NDataTable, NSpin, NTag, type DataTableColumns } from 'naive-ui'
import { listMenus } from '@/api/menus'
import type { AdminMenuTree } from '@/types/api'

const loading = ref(false)
const tree = ref<AdminMenuTree[]>([])

const columns: DataTableColumns<AdminMenuTree> = [
  { title: '标题', key: 'title', minWidth: 180 },
  { title: '名称', key: 'name', width: 140 },
  { title: '路径', key: 'path', ellipsis: { tooltip: true } },
  { title: '组件', key: 'component', ellipsis: { tooltip: true } },
  { title: '图标', key: 'icon', width: 100 },
  { title: '类型', key: 'type', width: 90 },
  { title: '权限码', key: 'permission', ellipsis: { tooltip: true } },
  { title: '排序', key: 'sort', width: 70 },
  {
    title: '可见',
    key: 'visible',
    width: 80,
    render: (row) =>
      h(NTag, { type: row.visible === false ? 'default' : 'success', size: 'small' }, () =>
        row.visible === false ? '隐藏' : '显示',
      ),
  },
]

async function load() {
  loading.value = true
  try {
    tree.value = (await listMenus()) || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">菜单管理</h1>
    </div>
    <div class="page-card">
      <NSpin :show="loading">
        <NDataTable
          :columns="columns"
          :data="tree"
          :row-key="(row: AdminMenuTree) => row.id"
          default-expand-all
          children-key="children"
        />
      </NSpin>
    </div>
  </div>
</template>
