<script setup lang="ts">
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
import { createDept, deleteDept, listDepts, updateDept, type AdminDept, type DeptPayload } from '@/api/depts'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const tree = ref<AdminDept[]>([])

const showForm = ref(false)
const editing = ref<AdminDept | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive<DeptPayload>({
  parentId: 0,
  name: '',
  sortOrder: 0,
  status: 1,
})

function flatten(nodes: AdminDept[], acc: AdminDept[] = []): AdminDept[] {
  for (const n of nodes) {
    acc.push(n)
    if (n.children?.length) flatten(n.children, acc)
  }
  return acc
}

const flatDepts = computed(() => flatten(tree.value))

function isDescendantOf(rootId: number, candidateId: number): boolean {
  const byId = new Map(flatDepts.value.map((d) => [d.id, d]))
  let current = byId.get(candidateId)
  let guard = 0
  while (current && guard++ < 64) {
    if (current.id === rootId) return true
    if (!current.parentId) return false
    current = byId.get(current.parentId)
  }
  return false
}

const parentOptions = computed<SelectOption[]>(() => {
  void locale.value
  const editingId = editing.value?.id
  const options: SelectOption[] = [{ label: t('dept.rootParent'), value: 0 }]
  for (const d of flatDepts.value) {
    if (editingId && (d.id === editingId || isDescendantOf(editingId, d.id))) continue
    options.push({ label: d.name, value: d.id })
  }
  return options
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

const columns = computed<DataTableColumns<AdminDept>>(() => {
  void locale.value
  return [
    { title: t('dept.name'), key: 'name', minWidth: 180 },
    { title: t('dept.sortOrder'), key: 'sortOrder', width: 80 },
    {
      title: t('common.status'),
      key: 'status',
      width: 90,
      render: (row) =>
        h(NTag, { type: row.status === 0 ? 'warning' : 'success', size: 'small' }, () =>
          row.status === 0 ? t('common.disabled') : t('common.enabled'),
        ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (row) => {
        const buttons: ReturnType<typeof h>[] = []
        if (auth.hasPermission('admin:dept:edit')) {
          buttons.push(
            h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit')),
          )
        }
        if (auth.hasPermission('admin:dept:create')) {
          buttons.push(
            h(
              NButton,
              { size: 'tiny', secondary: true, onClick: () => openCreate(row.id) },
              () => t('dept.addChild'),
            ),
          )
        }
        if (auth.hasPermission('admin:dept:delete')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                secondary: true,
                onClick: () =>
                  dialog.warning({
                    title: t('dept.deleteTitle'),
                    content: t('dept.deleteConfirm', { name: row.name }),
                    positiveText: t('common.delete'),
                    negativeText: t('common.cancel'),
                    onPositiveClick: async () => {
                      await deleteDept(row.id)
                      message.success(t('common.deleted'))
                      await load()
                    },
                  }),
              },
              () => t('common.delete'),
            ),
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
    tree.value = (await listDepts()) || []
  } finally {
    loading.value = false
  }
}

function resetForm(parentId = 0) {
  Object.assign(form, {
    parentId,
    name: '',
    sortOrder: 0,
    status: 1,
  })
}

function openCreate(parentId = 0) {
  editing.value = null
  resetForm(parentId)
  showForm.value = true
}

function openEdit(row: AdminDept) {
  const source = flatDepts.value.find((d) => d.id === row.id) || row
  editing.value = source
  Object.assign(form, {
    parentId: source.parentId ?? 0,
    name: source.name,
    sortOrder: source.sortOrder ?? 0,
    status: source.status ?? 1,
  })
  showForm.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: DeptPayload = {
      parentId: form.parentId ?? 0,
      name: form.name.trim(),
      sortOrder: form.sortOrder ?? 0,
      status: form.status ?? 1,
    }
    if (editing.value) {
      await updateDept(editing.value.id, payload)
      message.success(t('common.updateSuccess'))
    } else {
      await createDept(payload)
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
        <div class="page-hint">{{ t('dept.hint') }}</div>
        <NButton v-if="auth.hasPermission('admin:dept:create')" type="primary" @click="openCreate(0)">
          {{ t('dept.create') }}
        </NButton>
      </NSpace>
      <NSpin :show="loading">
        <NDataTable
          :columns="columns"
          :data="tree"
          :row-key="(row: AdminDept) => row.id"
          default-expand-all
          children-key="children"
          :scroll-x="640"
        />
      </NSpin>
    </div>

    <NModal
      v-model:show="showForm"
      preset="card"
      :title="editing ? t('dept.edit') : t('dept.create')"
      style="width: 480px"
    >
      <NForm ref="formRef" :model="form" label-placement="left" label-width="80">
        <NFormItem :label="t('dept.parent')" path="parentId">
          <NSelect v-model:value="form.parentId" :options="parentOptions" filterable />
        </NFormItem>
        <NFormItem
          :label="t('dept.name')"
          path="name"
          :rule="{ required: true, message: t('common.required') }"
        >
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem :label="t('dept.sortOrder')" path="sortOrder">
          <NInputNumber v-model:value="form.sortOrder" :min="0" style="width: 100%" />
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
    </NModal>
  </div>
</template>

<style scoped>
.page-hint {
  color: var(--n-text-color-3);
  font-size: 13px;
}
</style>
