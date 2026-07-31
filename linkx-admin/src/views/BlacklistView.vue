<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import {
  addBlacklist,
  listBlacklist,
  releaseBlacklist,
  type BlacklistItem,
} from '@/api/blacklist'
import { listUsers, type AdminUserListItem } from '@/api/users'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<BlacklistItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: 'active' })

const showAdd = ref(false)
const addSaving = ref(false)
const formRef = ref<FormInst | null>(null)
const userSearching = ref(false)
const userOptions = ref<{ label: string; value: string }[]>([])
const addForm = reactive({
  userId: null as string | null,
  reason: '',
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('blacklist.active'), value: 'active' },
    { label: t('blacklist.released'), value: 'released' },
  ]
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    userId: { required: true, message: t('blacklist.userRequired'), trigger: ['change', 'blur'] },
  }
})

function statusTag(status?: string) {
  const map: Record<string, 'error' | 'success' | 'default'> = {
    active: 'error',
    released: 'success',
  }
  const label: Record<string, string> = {
    active: t('blacklist.active'),
    released: t('blacklist.released'),
  }
  return h(NTag, { type: map[status || ''] || 'default', size: 'small' }, () => label[status || ''] || status || '-')
}

const columns = computed<DataTableColumns<BlacklistItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('user.username'),
      key: 'username',
      ellipsis: { tooltip: true },
      render: (row) =>
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            disabled: !row.userId || !auth.hasPermission('admin:user:view'),
            onClick: () => row.userId && router.push(`/admin/users/${row.userId}`),
          },
          () => row.username || row.userId || '-',
        ),
    },
    { title: t('user.nickname'), key: 'nickname', ellipsis: { tooltip: true } },
    { title: t('blacklist.reason'), key: 'reason', ellipsis: { tooltip: true } },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) => statusTag(row.status),
    },
    {
      title: t('blacklist.createdBy'),
      key: 'createdByName',
      width: 120,
      render: (row) => row.createdByName || row.createdBy || '-',
    },
    {
      title: t('blacklist.bannedAt'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('blacklist.releasedAt'),
      key: 'releasedAt',
      width: 170,
      render: (row) => formatTime(row.releasedAt),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 120,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          row.status === 'active' && auth.hasPermission('admin:blacklist:remove')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'primary',
                  secondary: true,
                  onClick: () => confirmRelease(row),
                },
                () => t('blacklist.release'),
              )
            : null,
        ]),
    },
  ]
})

function openAdd() {
  addForm.userId = null
  addForm.reason = ''
  userOptions.value = []
  showAdd.value = true
}

async function searchUsers(keyword: string) {
  const kw = keyword.trim()
  if (!kw) {
    userOptions.value = []
    return
  }
  userSearching.value = true
  try {
    const data = await listUsers({ page: 1, size: 20, keyword: kw })
    userOptions.value = (data.items || []).map((u: AdminUserListItem) => ({
      label: `${u.username}${u.nickname ? ` (${u.nickname})` : ''} · ${u.id}`,
      value: String(u.id),
    }))
  } finally {
    userSearching.value = false
  }
}

function confirmRelease(row: BlacklistItem) {
  dialog.warning({
    title: t('blacklist.releaseTitle'),
    content: t('blacklist.releaseConfirm', { user: row.username || row.userId || row.id }),
    positiveText: t('blacklist.release'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await releaseBlacklist(row.id)
      message.success(t('blacklist.releaseSuccess'))
      await load()
    },
  })
}

async function submitAdd() {
  await formRef.value?.validate()
  if (!addForm.userId) return
  addSaving.value = true
  try {
    await addBlacklist(addForm.userId, addForm.reason.trim() || undefined)
    message.success(t('blacklist.addSuccess'))
    showAdd.value = false
    query.status = 'active'
    query.page = 1
    await load()
  } finally {
    addSaving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listBlacklist({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      entryStatus: query.status || undefined,
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

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar">
        <SearchAutoComplete
          v-model="query.keyword"
          :placeholder="t('blacklist.searchPlaceholder')"
          width="220px"
          @search="search"
        />
        <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
        <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        <NButton
          v-if="auth.hasPermission('admin:blacklist:add')"
          type="error"
          secondary
          @click="openAdd"
        >
          {{ t('blacklist.add') }}
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

    <NModal v-model:show="showAdd" preset="card" :title="t('blacklist.addTitle')" style="width: 480px">
      <NForm ref="formRef" :model="addForm" :rules="rules" label-placement="top">
        <NFormItem :label="t('blacklist.user')" path="userId">
          <NSelect
            v-model:value="addForm.userId"
            filterable
            remote
            clearable
            :loading="userSearching"
            :options="userOptions"
            :placeholder="t('blacklist.userPlaceholder')"
            @search="searchUsers"
          />
        </NFormItem>
        <NFormItem :label="t('blacklist.reason')">
          <NInput
            v-model:value="addForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="t('blacklist.reasonPlaceholder')"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showAdd = false">{{ t('common.cancel') }}</NButton>
          <NButton type="error" :loading="addSaving" @click="submitAdd">
            {{ t('blacklist.add') }}
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
