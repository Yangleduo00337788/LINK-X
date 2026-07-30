<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSpace,
  NTag,
  NTree,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type TreeOption,
} from 'naive-ui'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  getRoleMenus,
  listRoles,
  updateRole,
  type AdminRole,
  type RolePayload,
} from '@/api/roles'
import { listMenus } from '@/api/menus'
import type { AdminMenuTree } from '@/types/api'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const items = ref<AdminRole[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const showForm = ref(false)
const editing = ref<AdminRole | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive<RolePayload>({ roleCode: '', roleName: '', description: '', status: 1 })
const saving = ref(false)

const showMenuModal = ref(false)
const menuRoleId = ref<number | null>(null)
const menuTree = ref<TreeOption[]>([])
const checkedKeys = ref<number[]>([])
const menuSaving = ref(false)

const columns: DataTableColumns<AdminRole> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '角色编码', key: 'roleCode' },
  { title: '角色名称', key: 'roleName' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' }, () =>
        row.status === 1 ? '启用' : '停用',
      ),
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
    width: 240,
    render: (row) =>
      h(NSpace, { size: 8 }, () => [
        auth.hasPermission('admin:role:edit')
          ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => '编辑')
          : null,
        auth.hasPermission('admin:role:assign-menu')
          ? h(NButton, { size: 'tiny', onClick: () => openMenus(row) }, () => '菜单')
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
                    title: '删除角色',
                    content: `确定删除角色「${row.roleName}」吗？`,
                    positiveText: '删除',
                    negativeText: '取消',
                    onPositiveClick: async () => {
                      await deleteRole(row.id)
                      message.success('已删除')
                      await load()
                    },
                  }),
              },
              () => '删除',
            )
          : null,
      ]),
  },
]

function toTree(nodes: AdminMenuTree[]): TreeOption[] {
  return nodes.map((n) => ({
    key: n.id,
    label: n.title || n.name,
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
      message.success('更新成功')
    } else {
      await createRole(form)
      message.success('创建成功')
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
    message.success('菜单权限已更新')
    showMenuModal.value = false
  } finally {
    menuSaving.value = false
  }
}

function onMenuChecked(keys: Array<string | number>) {
  checkedKeys.value = keys.map(Number)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">角色管理</h1>
      <NButton v-if="auth.hasPermission('admin:role:create')" type="primary" @click="openCreate">新建角色</NButton>
    </div>
    <div class="page-card">
      <NSpace style="margin-bottom: 16px">
        <NInput v-model:value="query.keyword" clearable placeholder="搜索角色" style="width: 220px" @keyup.enter="() => { query.page = 1; load() }" />
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

    <NModal v-model:show="showForm" preset="card" :title="editing ? '编辑角色' : '新建角色'" style="width: 480px">
      <NForm ref="formRef" :model="form" label-placement="left" label-width="80">
        <NFormItem label="编码" path="roleCode" :rule="{ required: true, message: '必填' }">
          <NInput v-model:value="form.roleCode" :disabled="!!editing" />
        </NFormItem>
        <NFormItem label="名称" path="roleName" :rule="{ required: true, message: '必填' }">
          <NInput v-model:value="form.roleName" />
        </NFormItem>
        <NFormItem label="描述">
          <NInput v-model:value="form.description" type="textarea" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="save">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showMenuModal" preset="card" title="分配菜单" style="width: 480px">
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
          <NButton @click="showMenuModal = false">取消</NButton>
          <NButton type="primary" :loading="menuSaving" @click="saveMenus">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
