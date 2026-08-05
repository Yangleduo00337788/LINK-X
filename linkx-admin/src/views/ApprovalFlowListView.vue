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
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type SelectOption,
} from 'naive-ui'
import {
  createApprovalFlow,
  deleteApprovalFlow,
  listApprovalFlows,
  parseApprovalSteps,
  stringifyApprovalSteps,
  updateApprovalFlow,
  type ApprovalFlowItem,
  type ApprovalFlowPayload,
  type ApprovalFlowStep,
} from '@/api/approvalFlows'
import { listRoles } from '@/api/roles'
import { listUsers } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<ApprovalFlowItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as '' | number })
const userOptions = ref<SelectOption[]>([])
const roleOptions = ref<SelectOption[]>([])

const showForm = ref(false)
const editing = ref<ApprovalFlowItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const steps = ref<ApprovalFlowStep[]>([])

const form = reactive<ApprovalFlowPayload>({
  name: '',
  bizType: 'review',
  description: '',
  stepsJson: '[]',
  enabled: true,
  autoStart: false,
  priority: 0,
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

const bizTypeOptions = computed(() => {
  void locale.value
  return [
    { label: t('approvalFlow.bizReview'), value: 'review' },
    { label: t('approvalFlow.bizFeedback'), value: 'feedback' },
    { label: t('approvalFlow.bizGeneric'), value: 'generic' },
  ]
})

const nodeTypeOptions = computed(() => {
  void locale.value
  return [
    { label: t('approvalFlow.nodeApprove'), value: 'approve' },
    { label: t('approvalFlow.nodeCountersign'), value: 'countersign' },
    { label: t('approvalFlow.nodeCc'), value: 'cc' },
  ]
})

const assigneeTypeOptions = computed(() => {
  void locale.value
  return [
    { label: t('approvalFlow.assigneeUser'), value: 'user' },
    { label: t('approvalFlow.assigneeRole'), value: 'role' },
  ]
})

const rules: FormRules = {
  name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  bizType: [{ required: true, message: () => t('common.required'), trigger: 'change' }],
}

const columns = computed<DataTableColumns<ApprovalFlowItem>>(() => {
  void locale.value
  return [
    { title: t('approvalFlow.name'), key: 'name', minWidth: 160 },
    { title: t('approvalFlow.bizType'), key: 'bizType', width: 100 },
    {
      title: t('common.status'),
      key: 'enabled',
      width: 90,
      render: (row) =>
        h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, () =>
          row.enabled ? t('common.enabled') : t('common.disabled')
        ),
    },
    {
      title: t('approvalFlow.autoStart'),
      key: 'autoStart',
      width: 100,
      render: (row) => (row.autoStart ? t('common.yes') : t('common.no')),
    },
    { title: t('approvalFlow.priority'), key: 'priority', width: 80 },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      render: (row) =>
        h(NSpace, { size: 4 }, () => [
          auth.hasPermission('admin:approval-flow:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:approval-flow:delete')
            ? h(
                NButton,
                { size: 'tiny', type: 'error', tertiary: true, onClick: () => onDelete(row) },
                () => t('common.delete')
              )
            : null,
        ]),
    },
  ]
})

function addStep() {
  steps.value.push({
    name: `${t('approvalFlow.step')} ${steps.value.length + 1}`,
    nodeType: 'approve',
    assigneeType: 'user',
    assigneeId: '',
  })
}

function removeStep(index: number) {
  steps.value.splice(index, 1)
}

async function loadOptions() {
  const tasks: Promise<void>[] = []
  if (auth.hasPermission('admin:user:list')) {
    tasks.push(
      listUsers({ page: 1, size: 200 }).then((users) => {
        userOptions.value = (users.items || []).map((u) => {
          const name = u.nickname || u.username || String(u.id)
          const account = u.username ? ` @${u.username}` : ''
          return {
            label: `${name}${account}`,
            value: String(u.id),
          }
        })
      })
    )
  }
  if (auth.hasPermission('admin:role:list')) {
    tasks.push(
      listRoles({ page: 1, size: 100 }).then((roles) => {
        roleOptions.value = (roles.items || []).map((r) => ({
          label: r.roleName || r.roleCode || String(r.id),
          value: String(r.id),
        }))
      })
    )
  }
  await Promise.all(tasks)
}

async function load() {
  loading.value = true
  try {
    const res = await listApprovalFlows({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
    })
    items.value = res.items || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  editing.value = null
  form.name = ''
  form.bizType = 'review'
  form.description = ''
  form.enabled = true
  form.autoStart = false
  form.priority = 0
  steps.value = [
    { name: t('approvalFlow.stepInitial'), nodeType: 'approve', assigneeType: 'user', assigneeId: '' },
  ]
  showForm.value = true
  await loadOptions()
}

async function openEdit(row: ApprovalFlowItem) {
  editing.value = row
  form.name = row.name || ''
  form.bizType = row.bizType || 'review'
  form.description = row.description || ''
  form.enabled = row.enabled !== false
  form.autoStart = !!row.autoStart
  form.priority = row.priority ?? 0
  steps.value = parseApprovalSteps(row.stepsJson)
  if (!steps.value.length) addStep()
  showForm.value = true
  await loadOptions()
}

async function save() {
  await formRef.value?.validate()
  if (!steps.value.length) {
    message.warning(t('approvalFlow.stepsRequired'))
    return
  }
  for (const step of steps.value) {
    if (!step.name?.trim()) {
      message.warning(t('approvalFlow.stepNameRequired'))
      return
    }
    if (step.assigneeType === 'role' && !step.assigneeId) {
      message.warning(t('approvalFlow.assigneeRequired'))
      return
    }
    if (step.assigneeType === 'user' && step.nodeType === 'countersign') {
      if (!step.assigneeIds?.length && !step.assigneeId) {
        message.warning(t('approvalFlow.assigneeRequired'))
        return
      }
    } else if (step.assigneeType === 'user' && !step.assigneeId) {
      message.warning(t('approvalFlow.assigneeRequired'))
      return
    }
  }
  saving.value = true
  try {
    const payload: ApprovalFlowPayload = {
      ...form,
      stepsJson: stringifyApprovalSteps(steps.value),
    }
    if (editing.value?.id) {
      await updateApprovalFlow(editing.value.id, payload)
      message.success(t('common.saveSuccess'))
    } else {
      await createApprovalFlow(payload)
      message.success(t('common.createSuccess'))
    }
    showForm.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function onDelete(row: ApprovalFlowItem) {
  dialog.warning({
    title: t('common.confirmDelete'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await deleteApprovalFlow(row.id)
      message.success(t('common.deleteSuccess'))
      await load()
    },
  })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <SearchAutoComplete
          v-model="query.keyword"
          :placeholder="t('approvalFlow.searchPlaceholder')"
          @search="
            () => {
              query.page = 1
              void load()
            }
          "
        />
        <NSpace>
          <NSelect
            v-model:value="query.status"
            :options="statusOptions"
            size="small"
            style="width: 120px"
            @update:value="
              () => {
                query.page = 1
                void load()
              }
            "
          />
          <NButton
            v-if="auth.hasPermission('admin:approval-flow:create')"
            type="primary"
            size="small"
            @click="openCreate"
          >
            {{ t('common.create') }}
          </NButton>
        </NSpace>
      </NSpace>

      <NDataTable
        :loading="loading"
        :columns="columns"
        :data="items"
        :bordered="false"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          onUpdatePage: (p: number) => {
            query.page = p
            void load()
          },
        }"
      />
    </div>

    <AdminFormShell
      v-model:show="showForm"
      :title="editing ? t('approvalFlow.editTitle') : t('approvalFlow.createTitle')"
      :width="720"
    >
      <div class="approval-flow-form-body">
        <NForm ref="formRef" :model="form" :rules="rules" label-placement="top">
          <NFormItem :label="t('approvalFlow.name')" path="name">
            <NInput v-model:value="form.name" />
          </NFormItem>
          <NFormItem :label="t('approvalFlow.bizType')" path="bizType">
            <NSelect v-model:value="form.bizType" :options="bizTypeOptions" />
          </NFormItem>
          <NFormItem :label="t('approvalFlow.description')">
            <NInput v-model:value="form.description" type="textarea" :rows="2" />
          </NFormItem>
          <div class="inline-fields">
            <NFormItem :label="t('approvalFlow.priority')">
              <NInputNumber v-model:value="form.priority" :min="0" style="width: 100%" />
            </NFormItem>
            <NFormItem :label="t('common.enabled')">
              <NSwitch v-model:value="form.enabled" />
            </NFormItem>
            <NFormItem :label="t('approvalFlow.autoStart')">
              <NSwitch v-model:value="form.autoStart" />
            </NFormItem>
          </div>
          <NFormItem :label="t('approvalFlow.steps')">
            <div class="steps-editor">
              <div v-for="(step, index) in steps" :key="index" class="step-card">
                <div class="step-card-head">
                  <span class="step-badge">{{ index + 1 }}</span>
                  <span class="step-card-title">{{ t('approvalFlow.step') }} {{ index + 1 }}</span>
                  <NButton size="tiny" tertiary type="error" @click="removeStep(index)">
                    {{ t('common.delete') }}
                  </NButton>
                </div>
                <NFormItem :label="t('approvalFlow.stepName')" :show-feedback="false" class="step-field">
                  <NInput v-model:value="step.name" :placeholder="t('approvalFlow.stepName')" />
                </NFormItem>
                <div class="step-grid">
                  <NFormItem :label="t('approvalInbox.nodeType')" :show-feedback="false" class="step-field">
                    <NSelect
                      v-model:value="step.nodeType"
                      :options="nodeTypeOptions"
                      :consistent-menu-width="false"
                      to="body"
                    />
                  </NFormItem>
                  <NFormItem :label="t('approvalFlow.assigneeType')" :show-feedback="false" class="step-field">
                    <NSelect
                      v-model:value="step.assigneeType"
                      :options="assigneeTypeOptions"
                      :consistent-menu-width="false"
                      to="body"
                    />
                  </NFormItem>
                </div>
                <NFormItem
                  v-if="step.assigneeType === 'role'"
                  :label="t('approvalFlow.pickRole')"
                  :show-feedback="false"
                  class="step-field"
                >
                  <NSelect
                    v-model:value="step.assigneeId"
                    :options="roleOptions"
                    filterable
                    :placeholder="t('approvalFlow.pickRole')"
                    :consistent-menu-width="false"
                    to="body"
                  />
                </NFormItem>
                <NFormItem
                  v-else-if="step.nodeType === 'countersign'"
                  :label="t('approvalFlow.pickUsers')"
                  :show-feedback="false"
                  class="step-field"
                >
                  <NSelect
                    v-model:value="step.assigneeIds"
                    :options="userOptions"
                    multiple
                    filterable
                    :placeholder="t('approvalFlow.pickUsers')"
                    :consistent-menu-width="false"
                    to="body"
                  />
                </NFormItem>
                <NFormItem
                  v-else
                  :label="t('approvalFlow.pickUser')"
                  :show-feedback="false"
                  class="step-field"
                >
                  <NSelect
                    v-model:value="step.assigneeId"
                    :options="userOptions"
                    filterable
                    :placeholder="t('approvalFlow.pickUser')"
                    :consistent-menu-width="false"
                    to="body"
                  />
                </NFormItem>
              </div>
              <NButton size="small" dashed block @click="addStep">{{ t('approvalFlow.addStep') }}</NButton>
            </div>
          </NFormItem>
        </NForm>
      </div>
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
.approval-flow-form-body {
  max-height: min(68vh, 680px);
  overflow-y: auto;
  padding-right: 4px;
}

.inline-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.steps-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.step-card {
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  padding: 12px;
  background: var(--n-color-modal);
}

.step-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--n-primary-color);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.step-card-title {
  flex: 1;
  font-weight: 600;
}

.step-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.step-field {
  margin-bottom: 0;
}

.step-field :deep(.n-form-item-blank) {
  width: 100%;
}

@media (max-width: 900px) {
  .inline-fields,
  .step-grid {
    grid-template-columns: 1fr;
  }
}
</style>
