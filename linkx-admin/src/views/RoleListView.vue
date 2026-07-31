<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAutoComplete,
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  NTree,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type TreeOption,
} from 'naive-ui'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'
import {
  assignRoleMenus,
  assignRoleUsers,
  createRole,
  deleteRole,
  getRoleMenus,
  getRoleUsers,
  listRoles,
  updateRole,
  type AdminRole,
  type RolePayload,
} from '@/api/roles'
import { listMenus } from '@/api/menus'
import { listUsers } from '@/api/users'
import type { AdminMenuTree } from '@/types/api'
import { formatTime } from '@/utils/format'
import { resolveMenuLabel } from '@/utils/menuI18n'
import { useAuthStore } from '@/stores/auth'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<AdminRole[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const showForm = ref(false)
const editing = ref<AdminRole | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive<RolePayload>({ roleCode: '', roleName: '', description: '', status: 1 })

const roleCodeOptions = computed(() => {
  const q = form.roleCode.trim().toLowerCase()
  const pool = [...new Set(items.value.map((r) => r.roleCode).filter(Boolean))]
  return (q ? pool.filter((x) => x.toLowerCase().includes(q)) : pool).map((value) => ({
    label: value,
    value,
  }))
})

const roleNameOptions = computed(() => {
  const q = form.roleName.trim().toLowerCase()
  const pool = [...new Set(items.value.map((r) => r.roleName).filter(Boolean))]
  return (q ? pool.filter((x) => x.toLowerCase().includes(q)) : pool).map((value) => ({
    label: value,
    value,
  }))
})
const saving = ref(false)

const showMenuModal = ref(false)
const menuRoleId = ref<number | null>(null)
const menuTree = ref<TreeOption[]>([])
const checkedKeys = ref<number[]>([])
const menuSaving = ref(false)

const showUserModal = ref(false)
const userRoleId = ref<number | null>(null)
const userOptions = ref<{ label: string; value: string }[]>([])
const selectedUserIds = ref<string[]>([])
const userSaving = ref(false)

const columns = computed<DataTableColumns<AdminRole>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('role.roleCode'), key: 'roleCode' },
    { title: t('role.roleName'), key: 'roleName' },
    { title: t('common.description'), key: 'description', ellipsis: { tooltip: true } },
    {
      title: t('common.status'),
      key: 'status',
      width: 90,
      render: (row) =>
        h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' }, () =>
          row.status === 1 ? t('common.enabled') : t('common.disabled'),
        ),
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
      width: 300,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:role:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:role:assign-menu')
            ? h(NButton, { size: 'tiny', onClick: () => openMenus(row) }, () => t('role.menus'))
            : null,
          auth.hasPermission('admin:role:assign-user') && row.roleCode !== 'user'
            ? h(NButton, { size: 'tiny', onClick: () => openUsers(row) }, () => t('role.users'))
            : null,
          auth.hasPermission('admin:role:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('role.deleteTitle'),
                      content: t('role.deleteConfirm', { name: row.roleName }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteRole(row.id)
                        message.success(t('common.deleted'))
                        await load()
                      },
                    }),
                },
                () => t('common.delete'),
              )
            : null,
        ]),
    },
  ]
})

function toTree(nodes: AdminMenuTree[]): TreeOption[] {
  return nodes.map((n) => ({
    key: n.id,
    label: resolveMenuLabel(t, n),
    children: n.children?.length ? toTree(n.children) : undefined,
  }))
}

async function load() {
  loading.value = true
  try {
    const data = await listRoles({
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
  Object.assign(form, { roleCode: '', roleName: '', description: '', status: 1 })
  showForm.value = true
}

function openEdit(row: AdminRole) {
  editing.value = row
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
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
      await updateRole(editing.value.id, form)
      message.success(t('common.updateSuccess'))
    } else {
      await createRole(form)
      message.success(t('common.createSuccess'))
    }
    showForm.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function openMenus(row: AdminRole) {
  menuRoleId.value = row.id
  const [menus, selected] = await Promise.all([listMenus(), getRoleMenus(row.id)])
  menuTree.value = toTree(menus || [])
  checkedKeys.value = selected || []
  showMenuModal.value = true
}

async function saveMenus() {
  if (!menuRoleId.value) return
  menuSaving.value = true
  try {
    await assignRoleMenus(menuRoleId.value, checkedKeys.value)
    message.success(t('role.menusUpdated'))
    showMenuModal.value = false
  } finally {
    menuSaving.value = false
  }
}

async function openUsers(row: AdminRole) {
  userRoleId.value = row.id
  const [assigned, candidates] = await Promise.all([
    getRoleUsers(row.id),
    listUsers({ page: 1, size: 100 }),
  ])
  const map = new Map<string, string>()
  for (const u of assigned || []) {
    map.set(String(u.id), `${u.username}${u.nickname ? ` (${u.nickname})` : ''}`)
  }
  for (const u of candidates.items || []) {
    map.set(String(u.id), `${u.username}${u.nickname ? ` (${u.nickname})` : ''}`)
  }
  userOptions.value = [...map.entries()].map(([value, label]) => ({ value, label }))
  selectedUserIds.value = (assigned || []).map((u) => String(u.id))
  showUserModal.value = true
}

async function saveUsers() {
  if (!userRoleId.value) return
  userSaving.value = true
  try {
    await assignRoleUsers(userRoleId.value, selectedUserIds.value)
    message.success(t('role.usersUpdated'))
    showUserModal.value = false
  } finally {
    userSaving.value = false
  }
}

function onMenuChecked(keys: Array<string | number>) {
  checkedKeys.value = keys.map(Number)
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
            :placeholder="t('role.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton v-if="auth.hasPermission('admin:role:create')" type="primary" @click="openCreate">
          {{ t('role.create') }}
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
        remote
      />
    </div>

    <NModal
      v-model:show="showForm"
      preset="card"
      :title="editing ? t('role.edit') : t('role.create')"
      style="width: 480px"
    >
      <NForm ref="formRef" :model="form" label-placement="left" label-width="80">
        <NFormItem
          :label="t('role.code')"
          path="roleCode"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NAutoComplete
            v-model:value="form.roleCode"
            :disabled="!!editing"
            :options="roleCodeOptions"
            :placeholder="t('role.roleCode')"
          />
        </NFormItem>
        <NFormItem
          :label="t('role.name')"
          path="roleName"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NAutoComplete
            v-model:value="form.roleName"
            :options="roleNameOptions"
            :placeholder="t('role.roleName')"
          />
        </NFormItem>
        <NFormItem :label="t('common.description')">
          <NInput v-model:value="form.description" type="textarea" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" @click="save">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showMenuModal" preset="card" :title="t('role.assignMenus')" style="width: 480px">
      <NTree
        block-line
        checkable
        cascade
        selectable
        :data="menuTree"
        :checked-keys="checkedKeys"
        @update:checked-keys="onMenuChecked"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showMenuModal = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="menuSaving" @click="saveMenus">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showUserModal" preset="card" :title="t('role.assignUsers')" style="width: 520px">
      <NSelect
        v-model:value="selectedUserIds"
        multiple
        filterable
        :options="userOptions"
        :placeholder="t('role.usersPlaceholder')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showUserModal = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="userSaving" @click="saveUsers">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
