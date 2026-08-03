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
  updateFeedbackDispatchRule,
  type FeedbackDispatchRuleItem,
  type FeedbackDispatchRulePayload,
} from '@/api/feedbackDispatchRules'
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

const showForm = ref(false)
const editing = ref<FeedbackDispatchRuleItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const form = reactive<FeedbackDispatchRulePayload>({
  name: '',
  feedbackType: '',
  keyword: '',
  assigneeId: '',
  priority: 0,
  enabled: true,
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

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('feedbackDispatch.nameRequired'), trigger: 'blur' }],
  assigneeId: [{ required: true, message: t('feedbackDispatch.assigneeRequired'), trigger: 'change' }],
}))

const columns = computed<DataTableColumns<FeedbackDispatchRuleItem>>(() => {
  void locale.value
  return [
    { title: t('feedbackDispatch.name'), key: 'name', width: 140 },
    { title: t('feedback.type'), key: 'feedbackType', width: 100, render: (row) => row.feedbackType || t('feedbackDispatch.anyType') },
    { title: t('feedbackDispatch.keyword'), key: 'keyword', width: 120, ellipsis: { tooltip: true } },
    { title: t('feedback.assignee'), key: 'assigneeName', width: 120 },
    { title: t('feedbackDispatch.priority'), key: 'priority', width: 90 },
    {
      title: t('common.status'),
      key: 'enabled',
      width: 90,
      render: (row) =>
        h(
          NTag,
          { type: row.enabled ? 'success' : 'default', size: 'small' },
          () => (row.enabled ? t('common.enabled') : t('common.disabled'))
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

function resetForm() {
  form.name = ''
  form.feedbackType = ''
  form.keyword = ''
  form.assigneeId = ''
  form.priority = 0
  form.enabled = true
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
  form.assigneeId = row.assigneeId || ''
  form.priority = row.priority ?? 0
  form.enabled = row.enabled !== false
  showForm.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body: FeedbackDispatchRulePayload = {
      name: form.name.trim(),
      feedbackType: form.feedbackType || undefined,
      keyword: form.keyword?.trim() || undefined,
      assigneeId: form.assigneeId,
      priority: form.priority ?? 0,
      enabled: form.enabled,
    }
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

async function loadAssignees() {
  const data = await listUsers({ page: 1, size: 100 })
  assigneeOptions.value = (data.items || []).map((u) => ({
    label: u.nickname || u.username || u.id,
    value: u.id,
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
  await Promise.all([loadAssignees(), load()])
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
        <NButton
          v-if="auth.hasPermission('admin:feedback-dispatch-rule:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('common.create') }}
        </NButton>
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

    <NModal
      v-model:show="showForm"
      preset="card"
      :title="editing ? t('feedbackDispatch.editTitle') : t('feedbackDispatch.createTitle')"
      style="width: 520px"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100">
        <NFormItem :label="t('feedbackDispatch.name')" path="name">
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem :label="t('feedback.type')" path="feedbackType">
          <NSelect v-model:value="form.feedbackType" :options="typeOptions" />
        </NFormItem>
        <NFormItem :label="t('feedbackDispatch.keyword')" path="keyword">
          <NInput v-model:value="form.keyword" :placeholder="t('feedbackDispatch.keywordHint')" />
        </NFormItem>
        <NFormItem :label="t('feedback.assignee')" path="assigneeId">
          <NSelect v-model:value="form.assigneeId" filterable :options="assigneeOptions" />
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
          <NButton type="primary" :loading="saving" @click="submitForm">{{ t('common.submit') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
