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
  type SelectOption,
} from 'naive-ui'
import {
  createDutySchedule,
  deleteDutySchedule,
  getDutySchedule,
  listDutySchedules,
  updateDutySchedule,
  type DutyScheduleItem,
  type DutySchedulePayload,
  type DutyScheduleSlot,
} from '@/api/dutySchedules'
import { listUsers } from '@/api/users'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<DutyScheduleItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as '' | number })
const assigneeOptions = ref<SelectOption[]>([])

const showForm = ref(false)
const editing = ref<DutyScheduleItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)

const form = reactive<DutySchedulePayload>({
  name: '',
  description: '',
  timezone: 'Asia/Shanghai',
  enabled: true,
  slots: [],
})

const weekdayOptions = computed(() => {
  void locale.value
  return [
    { label: t('dutySchedule.weekday1'), value: 1 },
    { label: t('dutySchedule.weekday2'), value: 2 },
    { label: t('dutySchedule.weekday3'), value: 3 },
    { label: t('dutySchedule.weekday4'), value: 4 },
    { label: t('dutySchedule.weekday5'), value: 5 },
    { label: t('dutySchedule.weekday6'), value: 6 },
    { label: t('dutySchedule.weekday7'), value: 7 },
  ]
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('common.enabled'), value: 1 },
    { label: t('common.disabled'), value: 0 },
  ]
})

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('dutySchedule.nameRequired'), trigger: 'blur' }],
}))

const columns = computed<DataTableColumns<DutyScheduleItem>>(() => {
  void locale.value
  return [
    { title: t('dutySchedule.name'), key: 'name', width: 160 },
    { title: t('dutySchedule.timezone'), key: 'timezone', width: 140 },
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
          auth.hasPermission('admin:duty-schedule:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          auth.hasPermission('admin:duty-schedule:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  onClick: () =>
                    dialog.warning({
                      title: t('common.delete'),
                      content: t('dutySchedule.deleteConfirm'),
                      positiveText: t('common.confirm'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteDutySchedule(row.id)
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

function emptySlot(): DutyScheduleSlot {
  return { weekday: 1, startTime: '09:00', endTime: '18:00', assigneeId: '', sortOrder: 0 }
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.timezone = 'Asia/Shanghai'
  form.enabled = true
  form.slots = [emptySlot()]
}

function openCreate() {
  editing.value = null
  resetForm()
  showForm.value = true
}

async function openEdit(row: DutyScheduleItem) {
  const detail = await getDutySchedule(row.id)
  editing.value = detail
  form.name = detail.name
  form.description = detail.description || ''
  form.timezone = detail.timezone || 'Asia/Shanghai'
  form.enabled = detail.enabled !== false
  form.slots = detail.slots?.length ? [...detail.slots] : [emptySlot()]
  showForm.value = true
}

function addSlot() {
  form.slots = [...(form.slots || []), emptySlot()]
}

function removeSlot(index: number) {
  form.slots = (form.slots || []).filter((_, i) => i !== index)
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body: DutySchedulePayload = {
      name: form.name.trim(),
      description: form.description?.trim() || undefined,
      timezone: form.timezone || 'Asia/Shanghai',
      enabled: form.enabled,
      slots: (form.slots || []).map((slot) => ({
        weekday: slot.weekday,
        startTime: slot.startTime,
        endTime: slot.endTime,
        assigneeId: slot.assigneeId,
        sortOrder: slot.sortOrder ?? 0,
      })),
    }
    if (editing.value) {
      await updateDutySchedule(editing.value.id, body)
      message.success(t('common.saveSuccess'))
    } else {
      await createDutySchedule(body)
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
    const data = await listDutySchedules({
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
            :placeholder="t('dutySchedule.searchPlaceholder')"
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
          v-if="auth.hasPermission('admin:duty-schedule:create')"
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
      :title="editing ? t('dutySchedule.editTitle') : t('dutySchedule.createTitle')"
      :width="720"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100">
        <NFormItem :label="t('dutySchedule.name')" path="name">
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem :label="t('dutySchedule.description')">
          <NInput v-model:value="form.description" type="textarea" :rows="2" />
        </NFormItem>
        <NFormItem :label="t('dutySchedule.timezone')">
          <NInput v-model:value="form.timezone" />
        </NFormItem>
        <NFormItem :label="t('common.enabled')">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
        <NFormItem :label="t('dutySchedule.slots')">
          <div class="slots">
            <div v-for="(slot, index) in form.slots" :key="index" class="slot-row">
              <NSelect
                v-model:value="slot.weekday"
                :options="weekdayOptions"
                style="width: 110px"
              />
              <NInput v-model:value="slot.startTime" placeholder="09:00" style="width: 90px" />
              <span>-</span>
              <NInput v-model:value="slot.endTime" placeholder="18:00" style="width: 90px" />
              <NSelect
                v-model:value="slot.assigneeId"
                filterable
                :options="assigneeOptions"
                style="flex: 1"
              />
              <NButton size="tiny" type="error" @click="removeSlot(index)">{{
                t('common.delete')
              }}</NButton>
            </div>
            <NButton size="small" @click="addSlot">{{ t('dutySchedule.addSlot') }}</NButton>
          </div>
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
  </div>
</template>

<style scoped>
.slots {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.slot-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
