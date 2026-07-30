<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NDataTable, NSpin, NTag, type DataTableColumns } from 'naive-ui'
import { listMenus } from '@/api/menus'
import type { AdminMenuTree } from '@/types/api'
import { resolveMenuLabel } from '@/utils/menuI18n'

const { t, locale } = useI18n()
const loading = ref(false)
const tree = ref<AdminMenuTree[]>([])

function mapTreeLabels(nodes: AdminMenuTree[]): AdminMenuTree[] {
  return nodes.map((n) => ({
    ...n,
    title: resolveMenuLabel(t, n),
    children: n.children?.length ? mapTreeLabels(n.children) : n.children,
  }))
}

const displayTree = computed(() => {
  void locale.value
  return mapTreeLabels(tree.value)
})

const columns = computed<DataTableColumns<AdminMenuTree>>(() => {
  void locale.value
  return [
    { title: t('menu.menuTitle'), key: 'title', minWidth: 180 },
    { title: t('menu.name'), key: 'name', width: 140 },
    { title: t('menu.path'), key: 'path', ellipsis: { tooltip: true } },
    { title: t('menu.component'), key: 'component', ellipsis: { tooltip: true } },
    { title: t('menu.icon'), key: 'icon', width: 100 },
    { title: t('menu.type'), key: 'type', width: 90 },
    { title: t('menu.permission'), key: 'permission', ellipsis: { tooltip: true } },
    { title: t('menu.sort'), key: 'sort', width: 70 },
    {
      title: t('menu.visible'),
      key: 'visible',
      width: 80,
      render: (row) =>
        h(NTag, { type: row.visible === false ? 'default' : 'success', size: 'small' }, () =>
          row.visible === false ? t('common.hide') : t('common.show'),
        ),
    },
  ]
})

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
    <div class="page-shell">
      <NSpin :show="loading">
        <NDataTable
          :columns="columns"
          :data="displayTree"
          :row-key="(row: AdminMenuTree) => row.id"
          default-expand-all
          children-key="children"
        />
      </NSpin>
    </div>
  </div>
</template>
