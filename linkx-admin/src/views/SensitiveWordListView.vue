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
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import {
  createSensitiveWord,
  deleteSensitiveWord,
  listSensitiveWords,
  updateSensitiveWord,
  type SensitiveWordItem,
  type SensitiveWordPayload,
} from '@/api/sensitiveWords'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<SensitiveWordItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as '' | number })

const showForm = ref(false)
const editing = ref<SensitiveWordItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const form = reactive<SensitiveWordPayload>({
  word: '',
  category: 'general',
  action: 'filter',
  replacement: '***',
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

const categoryOptions = computed(() => {
  void locale.value
  return [
    { label: t('sensitive.catGeneral'), value: 'general' },
    { label: t('sensitive.catPolitics'), value: 'politics' },
    { label: t('sensitive.catViolence'), value: 'violence' },
    { label: t('sensitive.catAd'), value: 'ad' },
  ]
})

const actionOptions = computed(() => {
  void locale.value
  return [
    { label: t('sensitive.actionFilter'), value: 'filter' },
    { label: t('sensitive.actionBlock'), value: 'block' },
    { label: t('sensitive.actionAlert'), value: 'alert' },
  ]
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    word: { required: true, message: t('sensitive.wordRequired'), trigger: ['blur', 'input'] },
    action: { required: true, message: t('sensitive.actionRequired'), trigger: ['change', 'blur'] },
  }
})

const columns = computed<DataTableColumns<SensitiveWordItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    { title: t('sensitive.word'), key: 'word', ellipsis: { tooltip: true } },
    {
      title: t('sensitive.category'),
      key: 'category',
      width: 110,
      render: (row) => categoryLabel(row.category),
    },
    {
      title: t('sensitive.action'),
      key: 'action',
      width: 100,
      render: (row) => actionLabel(row.action),
    },
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
      title: t('common.updateTime'),
      key: 'updateTime',
      width: 170,
      render: (row) => formatTime(row.updateTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:sensitive-word:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:sensitive-word:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('sensitive.deleteTitle'),
                      content: t('sensitive.deleteConfirm', { word: row.word }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteSensitiveWord(row.id)
                        message.success(t('sensitive.deleteSuccess'))
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

function categoryLabel(c?: string) {
  const map: Record<string, string> = {
    general: t('sensitive.catGeneral'),
    politics: t('sensitive.catPolitics'),
    violence: t('sensitive.catViolence'),
    ad: t('sensitive.catAd'),
  }
  return map[c || ''] || c || '-'
}

function actionLabel(a?: string) {
  const map: Record<string, string> = {
    filter: t('sensitive.actionFilter'),
    block: t('sensitive.actionBlock'),
    alert: t('sensitive.actionAlert'),
  }
  return map[a || ''] || a || '-'
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    word: '',
    category: 'general',
    action: 'filter',
    replacement: '***',
    enabled: true,
  })
  showForm.value = true
}

function openEdit(row: SensitiveWordItem) {
  editing.value = row
  Object.assign(form, {
    word: row.word,
    category: row.category || 'general',
    action: row.action || 'filter',
    replacement: row.action === 'filter' ? row.replacement || '***' : '***',
    enabled: row.enabled !== false,
  })
  showForm.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: SensitiveWordPayload = {
      word: form.word.trim(),
      category: form.category,
      action: form.action,
      replacement: form.replacement,
      enabled: form.enabled,
    }
    if (editing.value) {
      await updateSensitiveWord(editing.value.id, payload)
      message.success(t('sensitive.updateSuccess'))
    } else {
      await createSensitiveWord(payload)
      message.success(t('sensitive.createSuccess'))
    }
    showForm.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listSensitiveWords({
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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('sensitive.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:sensitive-word:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('sensitive.create') }}
        </NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="900"
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
      
      :title="editing ? t('sensitive.editTitle') : t('sensitive.createTitle')"
      
     :width="480">
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="90">
        <NFormItem :label="t('sensitive.word')" path="word">
          <NInput v-model:value="form.word" :placeholder="t('sensitive.wordPlaceholder')" />
        </NFormItem>
        <NFormItem :label="t('sensitive.category')" path="category">
          <NSelect v-model:value="form.category" :options="categoryOptions" />
        </NFormItem>
        <NFormItem :label="t('sensitive.action')" path="action">
          <NSelect v-model:value="form.action" :options="actionOptions" />
        </NFormItem>
        <NFormItem
          v-if="form.action === 'filter'"
          :label="t('sensitive.replacement')"
          path="replacement"
        >
          <NInput v-model:value="form.replacement" />
        </NFormItem>
        <NFormItem :label="t('common.status')" path="enabled">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" @click="submitForm">{{
            t('common.save')
          }}</NButton>
        </NSpace>
      </template>
    </AdminFormShell>
  </div>
</template>
