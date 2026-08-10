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
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NTag,
  NSpin,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type SelectOption,
} from 'naive-ui'
import { createMenu, deleteMenu, listMenus, reorderMenus, updateMenu } from '@/api/menus'
import type { AdminMenuPayload, AdminMenuTree } from '@/types/api'
import { resolveMenuLabel } from '@/utils/menuI18n'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const tree = ref<AdminMenuTree[]>([])

const showForm = ref(false)
const editing = ref<AdminMenuTree | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive<AdminMenuPayload>({
  parentId: 0,
  name: '',
  title: '',
  path: '',
  component: '',
  redirect: '',
  icon: '',
  menuType: 'menu',
  permissionCode: '',
  sortOrder: 0,
  hidden: 0,
  status: 1,
  remark: '',
})

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

function flatten(nodes: AdminMenuTree[], acc: AdminMenuTree[] = []): AdminMenuTree[] {
  for (const n of nodes) {
    acc.push(n)
    if (n.children?.length) flatten(n.children, acc)
  }
  return acc
}

const flatMenus = computed(() => flatten(tree.value))

const parentOptions = computed<SelectOption[]>(() => {
  void locale.value
  const editingId = editing.value?.id
  const options: SelectOption[] = [{ label: t('menu.rootParent'), value: 0 }]
  for (const m of flatMenus.value) {
    if (editingId && (m.id === editingId || isDescendantOf(editingId, m.id))) continue
    options.push({
      label: `${resolveMenuLabel(t, m)} (${m.name})`,
      value: m.id,
    })
  }
  return options
})

function isDescendantOf(rootId: number, candidateId: number): boolean {
  const byId = new Map(flatMenus.value.map((m) => [m.id, m]))
  let current = byId.get(candidateId)
  let guard = 0
  while (current && guard++ < 64) {
    if (current.id === rootId) return true
    if (!current.parentId) return false
    current = byId.get(current.parentId)
  }
  return false
}

function siblingsOf(row: AdminMenuTree): AdminMenuTree[] {
  if (!row.parentId) return tree.value
  const parent = flatMenus.value.find((m) => m.id === row.parentId)
  return parent?.children || []
}

async function move(row: AdminMenuTree, direction: -1 | 1) {
  if (!auth.hasPermission('admin:menu:reorder')) return
  const siblings = [...siblingsOf(row)].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id)
  const idx = siblings.findIndex((s) => s.id === row.id)
  const swapIdx = idx + direction
  if (idx < 0 || swapIdx < 0 || swapIdx >= siblings.length) return

  const next = [...siblings]
  ;[next[idx], next[swapIdx]] = [next[swapIdx], next[idx]]
  const items = next.map((s, i) => ({
    id: s.id,
    parentId: s.parentId ?? 0,
    sortOrder: i + 1,
  }))
  loading.value = true
  try {
    await reorderMenus(items)
    message.success(t('menu.reorderSuccess'))
    await load()
  } finally {
    loading.value = false
  }
}

const typeOptions = computed(() => {
  void locale.value
  return [
    { label: t('menu.typeDir'), value: 'dir' },
    { label: t('menu.typeMenu'), value: 'menu' },
    { label: t('menu.typeButton'), value: 'button' },
    { label: t('menu.typeApi'), value: 'api' },
  ]
})

const yesNoOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.show'), value: 0 },
    { label: t('common.hide'), value: 1 },
  ]
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

const columns = computed<DataTableColumns<AdminMenuTree>>(() => {
  void locale.value
  return [
    { title: t('menu.menuTitle'), key: 'title', minWidth: 160 },
    { title: t('menu.name'), key: 'name', width: 130 },
    { title: t('menu.path'), key: 'path', ellipsis: { tooltip: true } },
    { title: t('menu.component'), key: 'component', ellipsis: { tooltip: true }, width: 140 },
    { title: t('menu.icon'), key: 'icon', width: 90 },
    { title: t('menu.type'), key: 'type', width: 80 },
    { title: t('menu.permission'), key: 'permission', ellipsis: { tooltip: true }, width: 160 },
    { title: t('menu.sort'), key: 'sort', width: 60 },
    {
      title: t('menu.visible'),
      key: 'visible',
      width: 70,
      render: (row) =>
        h(NTag, { type: row.visible === false ? 'default' : 'success', size: 'small' }, () =>
          row.visible === false ? t('common.hide') : t('common.show')
        ),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 70,
      render: (row) =>
        h(NTag, { type: row.status === 0 ? 'warning' : 'success', size: 'small' }, () =>
          row.status === 0 ? t('common.disabled') : t('common.enabled')
        ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (row) => {
        const siblings = siblingsOf(row)
        const sorted = [...siblings].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id)
        const idx = sorted.findIndex((s) => s.id === row.id)
        const buttons: ReturnType<typeof h>[] = []
        if (auth.hasPermission('admin:menu:reorder')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                disabled: idx <= 0,
                onClick: () => move(row, -1),
              },
              () => t('menu.moveUp')
            ),
            h(
              NButton,
              {
                size: 'tiny',
                disabled: idx < 0 || idx >= sorted.length - 1,
                onClick: () => move(row, 1),
              },
              () => t('menu.moveDown')
            )
          )
        }
        if (auth.hasPermission('admin:menu:edit')) {
          buttons.push(
            h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
          )
        }
        if (auth.hasPermission('admin:menu:create')) {
          buttons.push(
            h(NButton, { size: 'tiny', secondary: true, onClick: () => openCreate(row.id) }, () =>
              t('menu.addChild')
            )
          )
        }
        if (auth.hasPermission('admin:menu:delete')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                secondary: true,
                onClick: () =>
                  dialog.warning({
                    title: t('menu.deleteTitle'),
                    content: t('menu.deleteConfirm', { title: resolveMenuLabel(t, row) }),
                    positiveText: t('common.delete'),
                    negativeText: t('common.cancel'),
                    onPositiveClick: async () => {
                      await deleteMenu(row.id)
                      message.success(t('common.deleted'))
                      await load()
                    },
                  }),
              },
              () => t('common.delete')
            )
          )
        }
        return h(NSpace, { size: 6, wrap: false }, () => buttons)
      },
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

function resetForm(parentId = 0) {
  Object.assign(form, {
    parentId,
    name: '',
    title: '',
    path: '',
    component: '',
    redirect: '',
    icon: '',
    menuType: 'menu',
    permissionCode: '',
    sortOrder: 0,
    hidden: 0,
    status: 1,
    remark: '',
  })
}

function openCreate(parentId = 0) {
  editing.value = null
  resetForm(parentId)
  showForm.value = true
}

function openEdit(row: AdminMenuTree) {
  const source = flatMenus.value.find((m) => m.id === row.id) || row
  editing.value = source
  Object.assign(form, {
    parentId: source.parentId ?? 0,
    name: source.name,
    title: source.title,
    path: source.path || '',
    component: source.component || '',
    redirect: source.redirect || '',
    icon: source.icon || '',
    menuType: source.type || 'menu',
    permissionCode: source.permission || '',
    sortOrder: source.sort ?? 0,
    hidden: source.visible === false ? 1 : 0,
    status: source.status ?? 1,
    remark: '',
  })
  showForm.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: AdminMenuPayload = {
      ...form,
      component: form.component || undefined,
      redirect: form.redirect || undefined,
      icon: form.icon || undefined,
      permissionCode: form.permissionCode || undefined,
      remark: form.remark || undefined,
    }
    if (editing.value) {
      await updateMenu(editing.value.id, payload)
      message.success(t('common.updateSuccess'))
    } else {
      await createMenu(payload)
      message.success(t('common.createSuccess'))
    }
    showForm.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <div class="page-hint">{{ t('menu.hint') }}</div>
        <NButton
          v-if="auth.hasPermission('admin:menu:create')"
          type="primary"
          @click="openCreate(0)"
        >
          {{ t('menu.create') }}
        </NButton>
      </NSpace>
      <NSpin :show="loading">
        <NDataTable
          :columns="columns"
          :data="displayTree"
          :row-key="(row: AdminMenuTree) => row.id"
          default-expand-all
          children-key="children"
          :scroll-x="1400"
        />
      </NSpin>
    </div>

    <AdminFormShell
      v-model:show="showForm"
      
      :title="editing ? t('menu.edit') : t('menu.create')"
      
     :width="560">
      <NForm ref="formRef" :model="form" label-placement="left" label-width="96">
        <NFormItem :label="t('menu.parent')" path="parentId">
          <NSelect v-model:value="form.parentId" :options="parentOptions" filterable />
        </NFormItem>
        <NFormItem
          :label="t('menu.name')"
          path="name"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NInput v-model:value="form.name" :disabled="!!editing" :placeholder="t('menu.namePh')" />
        </NFormItem>
        <NFormItem
          :label="t('menu.menuTitle')"
          path="title"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NInput v-model:value="form.title" />
        </NFormItem>
        <NFormItem
          :label="t('menu.path')"
          path="path"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NInput v-model:value="form.path" placeholder="/admin/xxx" />
        </NFormItem>
        <NFormItem :label="t('menu.component')" path="component">
          <NInput v-model:value="form.component" placeholder="views/XxxView" />
        </NFormItem>
        <NFormItem :label="t('menu.icon')" path="icon">
          <NInput v-model:value="form.icon" />
        </NFormItem>
        <NFormItem
          :label="t('menu.type')"
          path="menuType"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NSelect v-model:value="form.menuType" :options="typeOptions" />
        </NFormItem>
        <NFormItem :label="t('menu.permission')" path="permissionCode">
          <NInput v-model:value="form.permissionCode" placeholder="admin:xxx:list" />
        </NFormItem>
        <NFormItem :label="t('menu.sort')" path="sortOrder">
          <NInputNumber v-model:value="form.sortOrder" :min="0" style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('menu.visible')" path="hidden">
          <NSelect v-model:value="form.hidden" :options="yesNoOptions" />
        </NFormItem>
        <NFormItem :label="t('common.status')" path="status">
          <NSelect v-model:value="form.status" :options="statusOptions" />
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

<style scoped>
.page-hint {
  color: var(--n-text-color-3);
  font-size: 13px;
}
</style>
