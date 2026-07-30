<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  freezeUser,
  listUsers,
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

function canDisable() {
  return auth.hasPermission(['admin:user:freeze', 'admin:user:ban'])
}

function canEnable() {
  return auth.hasPermission(['admin:user:unfreeze', 'admin:user:unban'])
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
      width: 160,
      fixed: 'right',
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'tiny', onClick: () => router.push(`/admin/users/${row.id}`) }, () =>
            t('common.detail'),
          ),
          canToggleStatus(row) && row.status === 1 && canDisable()
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    confirmAction(t('user.freeze'), t('user.freezeConfirm'), () => freezeUser(row.id)),
                },
                () => t('user.freeze'),
              )
            : null,
          canToggleStatus(row) && row.status === 0 && canEnable()
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'primary',
                  secondary: true,
                  onClick: () =>
                    confirmAction(t('user.unfreeze'), t('user.unfreezeConfirm'), () =>
                      unfreezeUser(row.id),
                    ),
                },
                () => t('user.unfreeze'),
              )
            : null,
        ]),
    },
  ]
})

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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar">
        <SearchAutoComplete
          v-model="query.keyword"
          :placeholder="t('user.searchPlaceholder')"
          width="240px"
          @search="search"
        />
        <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
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
        :scroll-x="1100"
        remote
      />
    </div>
  </div>
</template>
