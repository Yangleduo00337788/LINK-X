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
  createRiskRule,
  deleteRiskRule,
  listRiskRules,
  simulateRiskRules,
  updateRiskRule,
  type RiskRuleItem,
  type RiskRulePayload,
  type RiskRuleSimulateResult,
} from '@/api/riskRules'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<RiskRuleItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as '' | number })

const showForm = ref(false)
const showSimulate = ref(false)
const editing = ref<RiskRuleItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const simulating = ref(false)
const simulateResult = ref<RiskRuleSimulateResult | null>(null)

const form = reactive<RiskRulePayload>({
  name: '',
  scope: 'simulate',
  keyword: '',
  conditionJson: '',
  scoreDelta: 10,
  actionType: 'score_only',
  priority: 0,
  enabled: true,
})

const simulateForm = reactive({
  scope: 'simulate',
  text: '',
  subjectUserId: '',
  messageCount: 0,
  memberCount: 0,
  taskRiskLevel: 'medium',
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

const scopeOptions = computed(() => {
  void locale.value
  return [
    { label: t('riskRule.scopeGlobal'), value: 'global' },
    { label: t('riskRule.scopeReview'), value: 'review' },
    { label: t('riskRule.scopeMessage'), value: 'message' },
    { label: t('riskRule.scopeSimulate'), value: 'simulate' },
  ]
})

const actionTypeOptions = computed(() => {
  void locale.value
  return [
    { label: t('riskRule.actionScoreOnly'), value: 'score_only' },
    { label: t('riskRule.actionBlock'), value: 'block' },
    { label: t('riskRule.actionAlert'), value: 'alert' },
    { label: t('riskRule.actionEscalate'), value: 'escalate' },
  ]
})

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('riskRule.nameRequired'), trigger: 'blur' }],
  keyword: [
    {
      validator: () => !!form.keyword?.trim() || !!form.conditionJson?.trim(),
      message: t('riskRule.matchRequired'),
      trigger: 'blur',
    },
  ],
}))

const columns = computed<DataTableColumns<RiskRuleItem>>(() => {
  void locale.value
  return [
    { title: t('riskRule.name'), key: 'name', width: 140 },
    {
      title: t('riskRule.scope'),
      key: 'scope',
      width: 100,
      render: (row) => scopeLabel(row.scope),
    },
    { title: t('riskRule.keyword'), key: 'keyword', width: 120, ellipsis: { tooltip: true } },
    { title: t('riskRule.scoreDelta'), key: 'scoreDelta', width: 90 },
    {
      title: t('riskRule.actionType'),
      key: 'actionType',
      width: 100,
      render: (row) => actionLabel(row.actionType),
    },
    { title: t('riskRule.priority'), key: 'priority', width: 80 },
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
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:risk-rule:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:risk-rule:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  onClick: () =>
                    dialog.warning({
                      title: t('common.delete'),
                      content: t('riskRule.deleteConfirm'),
                      positiveText: t('common.confirm'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteRiskRule(row.id)
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

function scopeLabel(value?: string) {
  const map: Record<string, string> = {
    global: t('riskRule.scopeGlobal'),
    review: t('riskRule.scopeReview'),
    message: t('riskRule.scopeMessage'),
    simulate: t('riskRule.scopeSimulate'),
  }
  return map[value || 'global'] || value || '-'
}

function actionLabel(value?: string) {
  const map: Record<string, string> = {
    score_only: t('riskRule.actionScoreOnly'),
    block: t('riskRule.actionBlock'),
    alert: t('riskRule.actionAlert'),
    escalate: t('riskRule.actionEscalate'),
  }
  return map[value || 'score_only'] || value || '-'
}

function resetForm() {
  form.name = ''
  form.scope = 'simulate'
  form.keyword = ''
  form.conditionJson = ''
  form.scoreDelta = 10
  form.actionType = 'score_only'
  form.priority = 0
  form.enabled = true
}

function openCreate() {
  editing.value = null
  resetForm()
  showForm.value = true
}

function openEdit(row: RiskRuleItem) {
  editing.value = row
  form.name = row.name
  form.scope = row.scope || 'global'
  form.keyword = row.keyword || ''
  form.conditionJson = row.conditionJson || ''
  form.scoreDelta = row.scoreDelta ?? 0
  form.actionType = row.actionType || 'score_only'
  form.priority = row.priority ?? 0
  form.enabled = row.enabled !== false
  showForm.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body: RiskRulePayload = {
      name: form.name.trim(),
      scope: form.scope || 'global',
      keyword: form.keyword?.trim() || undefined,
      conditionJson: form.conditionJson?.trim() || undefined,
      scoreDelta: form.scoreDelta ?? 0,
      actionType: form.actionType || 'score_only',
      priority: form.priority ?? 0,
      enabled: form.enabled,
    }
    if (editing.value) {
      await updateRiskRule(editing.value.id, body)
      message.success(t('common.saveSuccess'))
    } else {
      await createRiskRule(body)
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
    simulateResult.value = await simulateRiskRules({
      scope: simulateForm.scope,
      text: simulateForm.text || undefined,
      subjectUserId: simulateForm.subjectUserId.trim() || undefined,
      messageCount: simulateForm.messageCount || undefined,
      memberCount: simulateForm.memberCount || undefined,
      taskRiskLevel: simulateForm.taskRiskLevel || undefined,
    })
  } finally {
    simulating.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listRiskRules({
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

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('riskRule.searchPlaceholder')"
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
            v-if="auth.hasPermission('admin:risk-rule:simulate')"
            @click="showSimulate = true"
          >
            {{ t('riskRule.simulate') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:risk-rule:create')"
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
        :scroll-x="1000"
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
      :title="editing ? t('riskRule.editTitle') : t('riskRule.createTitle')"
      :width="640"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="110">
        <NFormItem :label="t('riskRule.name')" path="name">
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem :label="t('riskRule.scope')" path="scope">
          <NSelect v-model:value="form.scope" :options="scopeOptions" />
        </NFormItem>
        <NFormItem :label="t('riskRule.keyword')" path="keyword">
          <NInput v-model:value="form.keyword" :placeholder="t('riskRule.keywordHint')" />
        </NFormItem>
        <NFormItem :label="t('riskRule.conditionJson')" path="conditionJson">
          <NInput
            v-model:value="form.conditionJson"
            type="textarea"
            :rows="4"
            :placeholder="t('riskRule.conditionJsonHint')"
          />
        </NFormItem>
        <NFormItem :label="t('riskRule.scoreDelta')" path="scoreDelta">
          <NInputNumber v-model:value="form.scoreDelta" :min="-100" :max="100" style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('riskRule.actionType')" path="actionType">
          <NSelect v-model:value="form.actionType" :options="actionTypeOptions" />
        </NFormItem>
        <NFormItem :label="t('riskRule.priority')" path="priority">
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
      :title="t('riskRule.simulateTitle')"
      style="width: 560px"
    >
      <NForm label-placement="left" label-width="110">
        <NFormItem :label="t('riskRule.scope')">
          <NSelect v-model:value="simulateForm.scope" :options="scopeOptions" />
        </NFormItem>
        <NFormItem :label="t('riskPolicy.simulateText')">
          <NInput v-model:value="simulateForm.text" type="textarea" :rows="3" />
        </NFormItem>
        <NFormItem :label="t('riskPolicy.simulateUserId')">
          <NInput v-model:value="simulateForm.subjectUserId" />
        </NFormItem>
        <NFormItem :label="t('riskRule.messageCount')">
          <NInputNumber v-model:value="simulateForm.messageCount" :min="0" style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('riskRule.memberCount')">
          <NInputNumber v-model:value="simulateForm.memberCount" :min="0" style="width: 100%" />
        </NFormItem>
      </NForm>
      <NSpace justify="end" style="margin-top: 12px">
        <NButton :loading="simulating" type="primary" @click="runSimulate">
          {{ t('riskRule.runSimulate') }}
        </NButton>
      </NSpace>
      <div v-if="simulateResult" class="simulate-result">
        <p>{{ t('riskRule.ruleScoreDelta') }}: {{ simulateResult.scoreDelta ?? 0 }}</p>
        <p v-if="simulateResult.blocked">{{ t('riskRule.blocked') }}</p>
        <ul v-if="simulateResult.factors?.length">
          <li v-for="(f, i) in simulateResult.factors" :key="i">{{ f }}</li>
        </ul>
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
