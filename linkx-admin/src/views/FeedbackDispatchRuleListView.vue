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
  createFeedbackDispatchRule,
  deleteFeedbackDispatchRule,
  listFeedbackDispatchRules,
  simulateFeedbackDispatchRule,
  updateFeedbackDispatchRule,
  type FeedbackDispatchRuleItem,
  type FeedbackDispatchRulePayload,
  type FeedbackDispatchSimulateResult,
} from '@/api/feedbackDispatchRules'
import { listDutySchedules } from '@/api/dutySchedules'
import { listUsers } from '@/api/users'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<FeedbackDispatchRuleItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as '' | number })
const assigneeOptions = ref<SelectOption[]>([])
const dutyScheduleOptions = ref<SelectOption[]>([])
const roundRobinIds = ref<string[]>([])

const showForm = ref(false)
const showSimulate = ref(false)
const editing = ref<FeedbackDispatchRuleItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const simulating = ref(false)
const simulateResult = ref<FeedbackDispatchSimulateResult | null>(null)

const form = reactive<FeedbackDispatchRulePayload>({
  name: '',
  feedbackType: '',
  keyword: '',
  conditionJson: '',
  assigneeId: '',
  assigneeSource: 'fixed',
  dutyScheduleId: '',
  actionType: 'assign',
  actionConfig: '',
  notifyRoles: '',
  notifyChannels: 'sse',
  priority: 0,
  enabled: true,
})

const simulateForm = reactive({
  type: 'bug',
  content: '',
  status: 'pending',
  hasAssignee: false,
  createOffsetHours: 0,
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

const typeOptions = computed(() => {
  void locale.value
  return [
    { label: t('feedbackDispatch.anyType'), value: '' },
    { label: t('feedback.typeBug'), value: 'bug' },
    { label: t('feedback.typeSuggestion'), value: 'suggestion' },
    { label: t('feedback.typeOther'), value: 'other' },
  ]
})

const assigneeSourceOptions = computed(() => {
  void locale.value
  return [
    { label: t('feedbackDispatch.sourceFixed'), value: 'fixed' },
    { label: t('feedbackDispatch.sourceDuty'), value: 'duty' },
    { label: t('feedbackDispatch.sourceRoundRobin'), value: 'round_robin' },
  ]
})

const actionTypeOptions = computed(() => {
  void locale.value
  return [
    { label: t('feedbackDispatch.actionAssign'), value: 'assign' },
    { label: t('feedbackDispatch.actionNotify'), value: 'notify' },
    { label: t('feedbackDispatch.actionAssignNotify'), value: 'assign_notify' },
  ]
})

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('feedbackDispatch.nameRequired'), trigger: 'blur' }],
  assigneeId: [
    {
      validator: () => {
        if (!needsAssigneeAction()) return true
        if (form.assigneeSource !== 'fixed') return true
        return !!form.assigneeId
      },
      message: t('feedbackDispatch.assigneeRequired'),
      trigger: 'change',
    },
  ],
  dutyScheduleId: [
    {
      validator: () => form.assigneeSource !== 'duty' || needsAssigneeAction() || !!form.dutyScheduleId,
      message: t('feedbackDispatch.dutyScheduleRequired'),
      trigger: 'change',
    },
  ],
  roundRobinPool: [
    {
      validator: () =>
        form.assigneeSource !== 'round_robin' ||
        !needsAssigneeAction() ||
        roundRobinIds.value.length > 0,
      message: t('feedbackDispatch.roundRobinRequired'),
      trigger: 'change',
    },
  ],
}))

function needsAssigneeAction() {
  return form.actionType !== 'notify'
}

const columns = computed<DataTableColumns<FeedbackDispatchRuleItem>>(() => {
  void locale.value
  return [
    { title: t('feedbackDispatch.name'), key: 'name', width: 140 },
    {
      title: t('feedback.type'),
      key: 'feedbackType',
      width: 90,
      render: (row) => row.feedbackType || t('feedbackDispatch.anyType'),
    },
    {
      title: t('feedbackDispatch.actionType'),
      key: 'actionType',
      width: 110,
      render: (row) => actionLabel(row.actionType),
    },
    {
      title: t('feedbackDispatch.assigneeSource'),
      key: 'assigneeSource',
      width: 100,
      render: (row) => sourceLabel(row.assigneeSource),
    },
    { title: t('feedback.assignee'), key: 'assigneeName', width: 110 },
    { title: t('feedbackDispatch.priority'), key: 'priority', width: 80 },
    {
      title: t('common.status'),
      key: 'enabled',
      width: 90,
      render: (row) =>
        h(NTag, { type: row.enabled ? 'success' : 'default', size: 'small' }, () =>
          row.enabled ? t('common.enabled') : t('common.disabled')
        ),
    },
    {
      title: t('common.time'),
      key: 'updateTime',
      width: 170,
      render: (row) => formatTime(row.updateTime || row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:feedback-dispatch-rule:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:feedback-dispatch-rule:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  onClick: () =>
                    dialog.warning({
                      title: t('common.delete'),
                      content: t('feedbackDispatch.deleteConfirm'),
                      positiveText: t('common.confirm'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteFeedbackDispatchRule(row.id)
                        message.success(t('common.deleteSuccess'))
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

function actionLabel(value?: string) {
  const map: Record<string, string> = {
    assign: t('feedbackDispatch.actionAssign'),
    notify: t('feedbackDispatch.actionNotify'),
    assign_notify: t('feedbackDispatch.actionAssignNotify'),
  }
  return map[value || 'assign'] || value || '-'
}

function sourceLabel(value?: string) {
  const map: Record<string, string> = {
    fixed: t('feedbackDispatch.sourceFixed'),
    duty: t('feedbackDispatch.sourceDuty'),
    round_robin: t('feedbackDispatch.sourceRoundRobin'),
  }
  return map[value || 'fixed'] || value || '-'
}

function resetForm() {
  form.name = ''
  form.feedbackType = ''
  form.keyword = ''
  form.conditionJson = ''
  form.assigneeId = ''
  form.assigneeSource = 'fixed'
  form.dutyScheduleId = ''
  form.actionType = 'assign'
  form.actionConfig = ''
  form.notifyRoles = ''
  form.notifyChannels = 'sse'
  form.priority = 0
  form.enabled = true
  roundRobinIds.value = []
}

function parseRoundRobin(actionConfig?: string) {
  if (!actionConfig) return []
  try {
    const parsed = JSON.parse(actionConfig) as { assigneeIds?: string[] }
    return parsed.assigneeIds || []
  } catch {
    return []
  }
}

function openCreate() {
  editing.value = null
  resetForm()
  showForm.value = true
}

function openEdit(row: FeedbackDispatchRuleItem) {
  editing.value = row
  form.name = row.name
  form.feedbackType = row.feedbackType || ''
  form.keyword = row.keyword || ''
  form.conditionJson = row.conditionJson || ''
  form.assigneeId = row.assigneeId || ''
  form.assigneeSource = row.assigneeSource || 'fixed'
  form.dutyScheduleId = row.dutyScheduleId || ''
  form.actionType = row.actionType || 'assign'
  form.actionConfig = row.actionConfig || ''
  form.notifyRoles = row.notifyRoles || ''
  form.notifyChannels = row.notifyChannels || 'sse'
  form.priority = row.priority ?? 0
  form.enabled = row.enabled !== false
  roundRobinIds.value = parseRoundRobin(row.actionConfig)
  showForm.value = true
}

function buildPayload(): FeedbackDispatchRulePayload {
  const payload: FeedbackDispatchRulePayload = {
    name: form.name.trim(),
    feedbackType: form.feedbackType || undefined,
    keyword: form.keyword?.trim() || undefined,
    conditionJson: form.conditionJson?.trim() || undefined,
    assigneeSource: form.assigneeSource || 'fixed',
    actionType: form.actionType || 'assign',
    notifyRoles: form.notifyRoles?.trim() || undefined,
    notifyChannels: form.notifyChannels?.trim() || undefined,
    priority: form.priority ?? 0,
    enabled: form.enabled,
  }
  if (form.assigneeSource === 'fixed' && form.assigneeId) {
    payload.assigneeId = form.assigneeId
  }
  if (form.assigneeSource === 'duty' && form.dutyScheduleId) {
    payload.dutyScheduleId = form.dutyScheduleId
  }
  if (form.assigneeSource === 'round_robin' && needsAssigneeAction()) {
    payload.actionConfig = JSON.stringify({ assigneeIds: roundRobinIds.value })
  }
  return payload
}

async function submitForm() {
  if (form.assigneeSource === 'round_robin' && needsAssigneeAction() && roundRobinIds.value.length === 0) {
    message.error(t('feedbackDispatch.roundRobinRequired'))
    return
  }
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = buildPayload()
    if (editing.value) {
      await updateFeedbackDispatchRule(editing.value.id, body)
      message.success(t('common.saveSuccess'))
    } else {
      await createFeedbackDispatchRule(body)
      message.success(t('common.createSuccess'))
    }
    showForm.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function runSimulate() {
  simulating.value = true
  simulateResult.value = null
  try {
    simulateResult.value = await simulateFeedbackDispatchRule({
      type: simulateForm.type || undefined,
      content: simulateForm.content || undefined,
      status: simulateForm.status || undefined,
      hasAssignee: simulateForm.hasAssignee,
      createOffsetHours: simulateForm.createOffsetHours,
    })
  } finally {
    simulating.value = false
  }
}

async function loadAssignees() {
  const data = await listUsers({ page: 1, size: 100 })
  assigneeOptions.value = (data.items || []).map((u) => ({
    label: u.nickname || u.username || u.id,
    value: u.id,
  }))
}

async function loadDutySchedules() {
  const data = await listDutySchedules({ page: 1, size: 100, status: 1 })
  dutyScheduleOptions.value = (data.items || []).map((s) => ({
    label: s.name,
    value: s.id,
  }))
}

async function load() {
  loading.value = true
  try {
    const data = await listFeedbackDispatchRules({
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

onMounted(async () => {
  await Promise.all([loadAssignees(), loadDutySchedules(), load()])
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('feedbackDispatch.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.status"
            :options="statusOptions"
            style="width: 140px"
            @update:value="search"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NSpace>
          <NButton
            v-if="auth.hasPermission('admin:feedback-dispatch-rule:simulate')"
            @click="showSimulate = true"
          >
            {{ t('feedbackDispatch.simulate') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:feedback-dispatch-rule:create')"
            type="primary"
            @click="openCreate"
          >
            {{ t('common.create') }}
          </NButton>
        </NSpace>
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
      :title="editing ? t('feedbackDispatch.editTitle') : t('feedbackDispatch.createTitle')"
      :width="640"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="110">
        <NFormItem :label="t('feedbackDispatch.name')" path="name">
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem :label="t('feedback.type')" path="feedbackType">
          <NSelect v-model:value="form.feedbackType" :options="typeOptions" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.keyword')" path="keyword">
          <NInput v-model:value="form.keyword" :placeholder="t('feedbackDispatch.keywordHint')" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.conditionJson')" path="conditionJson">
          <NInput
            v-model:value="form.conditionJson"
            type="textarea"
            :placeholder="t('feedbackDispatch.conditionJsonHint')"
            :rows="4"
          />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.actionType')" path="actionType">
          <NSelect v-model:value="form.actionType" :options="actionTypeOptions" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.assigneeSource')" path="assigneeSource">
          <NSelect v-model:value="form.assigneeSource" :options="assigneeSourceOptions" />
        </NFormItem>
        <NFormItem
          v-if="form.assigneeSource === 'fixed' && form.actionType !== 'notify'"
          :label="t('feedback.assignee')"
          path="assigneeId"
        >
          <NSelect v-model:value="form.assigneeId" filterable :options="assigneeOptions" />
        </NFormItem>
        <NFormItem
          v-if="form.assigneeSource === 'duty' && form.actionType !== 'notify'"
          :label="t('feedbackDispatch.dutySchedule')"
          path="dutyScheduleId"
        >
          <NSelect v-model:value="form.dutyScheduleId" filterable :options="dutyScheduleOptions" />
        </NFormItem>
        <NFormItem
          v-if="form.assigneeSource === 'round_robin' && needsAssigneeAction()"
          :label="t('feedbackDispatch.roundRobinPool')"
          path="roundRobinPool"
        >
          <NSelect
            v-model:value="roundRobinIds"
            filterable
            multiple
            :options="assigneeOptions"
            :placeholder="t('feedbackDispatch.roundRobinHint')"
          />
        </NFormItem>
        <NFormItem
          v-if="form.actionType === 'notify' || form.actionType === 'assign_notify'"
          :label="t('feedbackDispatch.notifyRoles')"
        >
          <NInput
            v-model:value="form.notifyRoles"
            :placeholder="t('feedbackDispatch.notifyRolesHint')"
          />
        </NFormItem>
        <NFormItem
          v-if="form.actionType === 'notify' || form.actionType === 'assign_notify'"
          :label="t('feedbackDispatch.notifyChannels')"
        >
          <NInput v-model:value="form.notifyChannels" :placeholder="'sse,email'" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.priority')" path="priority">
          <NInputNumber v-model:value="form.priority" :min="-100" :max="1000" style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('common.enabled')" path="enabled">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" @click="submitForm">{{
            t('common.submit')
          }}</NButton>
        </NSpace>
      </template>
    </AdminFormShell>

    <NModal
      v-model:show="showSimulate"
      preset="card"
      :title="t('feedbackDispatch.simulateTitle')"
      style="width: 560px"
    >
      <NForm label-placement="left" label-width="110">
        <NFormItem :label="t('feedback.type')">
          <NSelect v-model:value="simulateForm.type" :options="typeOptions.filter((o) => o.value)" />
        </NFormItem>
        <NFormItem :label="t('feedback.content')">
          <NInput v-model:value="simulateForm.content" type="textarea" :rows="3" />
        </NFormItem>
        <NFormItem :label="t('common.status')">
          <NInput v-model:value="simulateForm.status" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.hasAssignee')">
          <NSwitch v-model:value="simulateForm.hasAssignee" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.createOffsetHours')">
          <NInputNumber v-model:value="simulateForm.createOffsetHours" style="width: 100%" />
        </NFormItem>
      </NForm>
      <NSpace justify="end" style="margin-top: 12px">
        <NButton :loading="simulating" type="primary" @click="runSimulate">
          {{ t('feedbackDispatch.runSimulate') }}
        </NButton>
      </NSpace>
      <div v-if="simulateResult" class="simulate-result">
        <p>
          <strong>{{ t('feedbackDispatch.simulateMatched') }}:</strong>
          {{ simulateResult.matched ? t('common.yes') : t('common.no') }}
        </p>
        <template v-if="simulateResult.matched">
          <p>{{ t('feedbackDispatch.name') }}: {{ simulateResult.ruleName }}</p>
          <p>{{ t('feedbackDispatch.actionType') }}: {{ actionLabel(simulateResult.actionType) }}</p>
          <p v-if="simulateResult.assigneeName">
            {{ t('feedback.assignee') }}: {{ simulateResult.assigneeName }}
          </p>
          <p v-if="simulateResult.notifyRoles">
            {{ t('feedbackDispatch.notifyRoles') }}: {{ simulateResult.notifyRoles }}
          </p>
        </template>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.simulate-result {
  margin-top: 16px;
  padding: 12px;
  background: var(--n-color-modal);
  border-radius: 8px;
}
</style>
