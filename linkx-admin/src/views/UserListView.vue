<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NInput,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  banUser,
  freezeUser,
  listUsers,
  unbanUser,
  unfreezeUser,
  type AdminUserListItem,
} from '@/api/users'
import { formatTime, userStatusLabel, userStatusType } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const items = ref<AdminUserListItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '' as '' | number,
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 1 },
  { label: '禁用', value: 0 },
]

const columns: DataTableColumns<AdminUserListItem> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '用户名', key: 'username', ellipsis: { tooltip: true } },
  { title: '昵称', key: 'nickname', ellipsis: { tooltip: true } },
  { title: '邮箱', key: 'email', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) => h(NTag, { type: userStatusType(row.status), size: 'small' }, () => userStatusLabel(row.status)),
  },
  {
    title: '角色',
    key: 'roles',
    render: (row) => (row.roles?.length ? row.roles.join(', ') : '暂无'),
  },
  {
    title: '创建时间',
    key: 'createTime',
    width: 170,
    render: (row) => formatTime(row.createTime),
  },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    fixed: 'right',
    render: (row) =>
      h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'tiny', onClick: () => router.push(`/admin/users/${row.id}`) }, () => '详情'),
        row.status === 1 && auth.hasPermission('admin:user:freeze')
          ? h(NButton, { size: 'tiny', onClick: () => confirmAction('冻结', () => freezeUser(row.id)) }, () => '冻结')
          : null,
        row.status === 0 && auth.hasPermission('admin:user:unfreeze')
          ? h(NButton, { size: 'tiny', onClick: () => confirmAction('解冻', () => unfreezeUser(row.id)) }, () => '解冻')
          : null,
        auth.hasPermission('admin:user:ban')
          ? h(NButton, { size: 'tiny', type: 'error', secondary: true, onClick: () => confirmAction('封禁', () => banUser(row.id)) }, () => '封禁')
          : null,
        auth.hasPermission('admin:user:unban')
          ? h(NButton, { size: 'tiny', onClick: () => confirmAction('解封', () => unbanUser(row.id)) }, () => '解封')
          : null,
      ]),
  },
]

function confirmAction(label: string, action: () => Promise<unknown>) {
  dialog.warning({
    title: `确认${label}`,
    content: `确定要${label}该用户吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      await action()
      message.success(`${label}成功`)
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
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
    </div>
    <div class="page-card">
      <NSpace style="margin-bottom: 16px">
        <NInput v-model:value="query.keyword" clearable placeholder="搜索用户名/昵称/邮箱" style="width: 240px" @keyup.enter="search" />
        <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
        <NButton type="primary" @click="search">查询</NButton>
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
