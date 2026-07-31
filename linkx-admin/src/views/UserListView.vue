<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDropdown,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type DropdownOption,
} from 'naive-ui'
import {
  banUser,
  exportUsers,
  freezeUser,
  listUsers,
  unbanUser,
  unfreezeUser,
  type AdminUserListItem,
} from '@/api/users'
import { formatTime, userStatusLabel, userStatusType } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const items = ref<AdminUserListItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '' as '' | number,
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('common.normal'), value: 1 },
    { label: t('common.frozen'), value: 0 },
  ]
})

const ADMIN_ROLES = new Set(['admin', 'super_admin'])

function canToggleStatus(row: AdminUserListItem) {
  if (String(row.id) === String(auth.user?.id)) return false
  if (row.roles?.some((r) => ADMIN_ROLES.has(r))) return false
  return true
}

function actionOptions(row: AdminUserListItem): DropdownOption[] {
  if (!canToggleStatus(row)) return []
  const opts: DropdownOption[] = []
  if (row.status === 1) {
    if (auth.hasPermission('admin:user:freeze')) {
      opts.push({ label: t('user.freeze'), key: 'freeze' })
    }
    if (auth.hasPermission('admin:user:ban')) {
      opts.push({ label: t('user.ban'), key: 'ban' })
    }
  } else {
    if (auth.hasPermission('admin:user:unfreeze')) {
      opts.push({ label: t('user.unfreeze'), key: 'unfreeze' })
    }
    if (auth.hasPermission('admin:user:unban')) {
      opts.push({ label: t('user.unban'), key: 'unban' })
    }
  }
  return opts
}

const columns = computed<DataTableColumns<AdminUserListItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('user.username'), key: 'username', ellipsis: { tooltip: true } },
    { title: t('user.nickname'), key: 'nickname', ellipsis: { tooltip: true } },
    { title: t('user.email'), key: 'email', ellipsis: { tooltip: true } },
    {
      title: t('common.status'),
      key: 'status',
      width: 90,
      render: (row) =>
        h(NTag, { type: userStatusType(row.status), size: 'small' }, () => userStatusLabel(row.status)),
    },
    {
      title: t('user.roles'),
      key: 'roles',
      render: (row) => (row.roles?.length ? row.roles.join(', ') : t('common.none')),
    },
    {
      title: t('common.createTime'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (row) => {
        const opts = actionOptions(row)
        return h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'tiny', onClick: () => router.push(`/admin/users/${row.id}`) }, () =>
            t('common.detail'),
          ),
          opts.length
            ? h(
                NDropdown,
                {
                  options: opts,
                  onSelect: (key: string) => handleAction(row, key),
                },
                () => h(NButton, { size: 'tiny' }, () => t('common.actions')),
              )
            : null,
        ])
      },
    },
  ]
})

function handleAction(row: AdminUserListItem, key: string) {
  const map: Record<string, { label: string; content: string; action: () => Promise<unknown> }> = {
    freeze: {
      label: t('user.freeze'),
      content: t('user.freezeConfirm'),
      action: () => freezeUser(row.id),
    },
    unfreeze: {
      label: t('user.unfreeze'),
      content: t('user.unfreezeConfirm'),
      action: () => unfreezeUser(row.id),
    },
    ban: {
      label: t('user.ban'),
      content: t('user.banConfirm'),
      action: () => banUser(row.id),
    },
    unban: {
      label: t('user.unban'),
      content: t('user.unbanConfirm'),
      action: () => unbanUser(row.id),
    },
  }
  const item = map[key]
  if (!item) return
  confirmAction(item.label, item.content, item.action)
}

function confirmAction(label: string, content: string, action: () => Promise<unknown>) {
  dialog.warning({
    title: t('common.confirmAction', { action: label }),
    content,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await action()
      message.success(t('common.actionSuccess', { action: label }))
      await load()
    },
  })
}

async function load() {
  loading.value = true
  try {
    const data = await listUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
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
    await exportUsers({
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
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
            :placeholder="t('user.searchPlaceholder')"
            width="240px"
            @search="search"
          />
          <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:user:export')"
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
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p: number) => { query.page = p; load() },
          onUpdatePageSize: (s: number) => { query.size = s; query.page = 1; load() },
        }"
        :scroll-x="1100"
        remote
      />
    </div>
  </div>
</template>
