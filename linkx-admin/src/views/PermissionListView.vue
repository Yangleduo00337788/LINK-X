<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NDataTable, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { listPermissions, type AdminPermission } from '@/api/menus'
import { resolvePermissionDesc, resolvePermissionName } from '@/utils/menuI18n'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const { t, locale } = useI18n()
const loading = ref(false)
const items = ref<AdminPermission[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const columns = computed<DataTableColumns<AdminPermission>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('permission.permissionCode'), key: 'permissionCode', ellipsis: { tooltip: true } },
    {
      title: t('permission.permissionName'),
      key: 'permissionName',
      render: (row) => resolvePermissionName(t, row.permissionCode, row.permissionName),
    },
    { title: t('permission.resourceType'), key: 'resourceType', width: 120 },
    { title: t('permission.resourcePath'), key: 'resourcePath', ellipsis: { tooltip: true } },
    {
      title: t('common.description'),
      key: 'description',
      ellipsis: { tooltip: true },
      render: (row) => resolvePermissionDesc(t, row.permissionCode, row.description),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 90,
      render: (row) =>
        h(NTag, { type: row.status === 1 ? 'success' : 'default', size: 'small' }, () =>
          row.status === 1 ? t('common.enabled') : t('common.disabled'),
        ),
    },
  ]
})

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

function search() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar">
        <SearchAutoComplete
          v-model="query.keyword"
          :placeholder="t('permission.searchPlaceholder')"
          width="240px"
          @search="search"
        />
        <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
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
