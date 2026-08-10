<!-- 作者：yangleduo -->
<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
import { computed, h, onMounted, reactive, ref } from 'vue'
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
} from 'naive-ui'
import {
  createPermission,
  deletePermission,
  listPermissions,
  updatePermission,
  type AdminPermission,
  type PermissionPayload,
} from '@/api/menus'
import { resolvePermissionDesc, resolvePermissionName } from '@/utils/menuI18n'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const items = ref<AdminPermission[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const showForm = ref(false)
const editing = ref<AdminPermission | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive<PermissionPayload>({
  permissionCode: '',
  permissionName: '',
  resourceType: 'button',
  resourcePath: '',
  description: '',
  status: 1,
})

const typeOptions = computed(() => {
  void locale.value
  return [
    { label: 'page', value: 'page' },
    { label: 'button', value: 'button' },
    { label: 'api', value: 'api' },
  ]
})

const statusFormOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

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
    {
      title: t('permission.resourcePath'),
      key: 'resourcePath',
      width: 200,
      ellipsis: { tooltip: true },
      render: (row) => {
        if (row.resourcePath) return row.resourcePath
        if (row.resourceType === 'button') return t('permission.noResourcePath')
        return t('common.none')
      },
    },
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
          row.status === 1 ? t('common.enabled') : t('common.disabled')
        ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:permission:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:permission:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('permission.deleteTitle'),
                      content: t('permission.deleteConfirm', { code: row.permissionCode }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deletePermission(row.id)
                        message.success(t('common.deleted'))
                        await load()
                      },
                    }),
                },
                () => t('common.delete')
              )
            : null,
        ]),
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

function openCreate() {
  editing.value = null
  Object.assign(form, {
    permissionCode: '',
    permissionName: '',
    resourceType: 'button',
    resourcePath: '',
    description: '',
    status: 1,
  })
  showForm.value = true
}

function openEdit(row: AdminPermission) {
  editing.value = row
  Object.assign(form, {
    permissionCode: row.permissionCode,
    permissionName: row.permissionName,
    resourceType: row.resourceType || 'button',
    resourcePath: row.resourcePath || '',
    description: row.description || '',
    status: row.status ?? 1,
  })
  showForm.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updatePermission(editing.value.id, form)
      message.success(t('common.updateSuccess'))
    } else {
      await createPermission(form)
      message.success(t('common.createSuccess'))
    }
    showForm.value = false
    await load()
  } finally {
    saving.value = false
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
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('permission.searchPlaceholder')"
            width="240px"
            @search="search"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:permission:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('permission.create') }}
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
          onUpdatePage: (p: number) => {
            query.page = p
            load()
          },
          onUpdatePageSize: (s: number) => {
            query.size = s
            query.page = 1
            load()
          },
        }"
        remote
      />
    </div>

    <AdminFormShell
      v-model:show="showForm"
      
      :title="editing ? t('permission.edit') : t('permission.create')"
      
     :width="520">
      <NForm ref="formRef" :model="form" label-placement="left" label-width="100">
        <NFormItem
          :label="t('permission.permissionCode')"
          path="permissionCode"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NInput v-model:value="form.permissionCode" :disabled="!!editing" />
        </NFormItem>
        <NFormItem
          :label="t('permission.permissionName')"
          path="permissionName"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NInput v-model:value="form.permissionName" />
        </NFormItem>
        <NFormItem :label="t('permission.resourceType')">
          <NSelect v-model:value="form.resourceType" :options="typeOptions" />
        </NFormItem>
        <NFormItem :label="t('permission.resourcePath')">
          <NInput v-model:value="form.resourcePath" />
        </NFormItem>
        <NFormItem :label="t('common.description')">
          <NInput v-model:value="form.description" type="textarea" :rows="2" />
        </NFormItem>
        <NFormItem :label="t('common.status')">
          <NSelect v-model:value="form.status" :options="statusFormOptions" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" @click="save">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </AdminFormShell>
  </div>
</template>
