<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NForm,
  NFormItem,
  NImage,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import dayjs from 'dayjs'
import {
  createActivity,
  deleteActivity,
  listActivities,
  publishActivity,
  unpublishActivity,
  updateActivity,
  uploadActivityCover,
  type ActivityItem,
  type ActivityPayload,
} from '@/api/activities'
import { formatTime } from '@/utils/format'
import { resolveActivitySrc } from '@/utils/mediaUrl'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<ActivityItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  activityStatus: '',
})

const showForm = ref(false)
const editing = ref<ActivityItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)
const form = reactive<{
  title: string
  coverKey: string
  previewUrl: string
  linkUrl: string
  description: string
  sortOrder: number
  startAt: number | null
  endAt: number | null
}>({
  title: '',
  coverKey: '',
  previewUrl: '',
  linkUrl: '',
  description: '',
  sortOrder: 0,
  startAt: null,
  endAt: null,
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('activity.draft'), value: 'draft' },
    { label: t('activity.published'), value: 'published' },
    { label: t('activity.unpublished'), value: 'unpublished' },
  ]
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    title: { required: true, message: t('activity.titleRequired'), trigger: ['blur', 'input'] },
    coverKey: {
      required: true,
      message: t('activity.coverRequired'),
      trigger: ['change', 'blur'],
      validator: () => {
        if (!form.coverKey.trim()) return new Error(t('activity.coverRequired'))
        return true
      },
    },
  }
})

function statusLabel(status?: string) {
  const label: Record<string, string> = {
    draft: t('activity.draft'),
    published: t('activity.published'),
    unpublished: t('activity.unpublished'),
  }
  return label[status || ''] || status || '-'
}

function statusType(status?: string): 'default' | 'success' | 'warning' | 'error' {
  const map: Record<string, 'default' | 'success' | 'warning' | 'error'> = {
    draft: 'default',
    published: 'success',
    unpublished: 'warning',
  }
  return map[status || ''] || 'default'
}

function toMillis(value?: string | number | Date | null): number | null {
  if (value == null || value === '') return null
  const d = dayjs(value)
  return d.isValid() ? d.valueOf() : null
}

const columns = computed<DataTableColumns<ActivityItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('activity.preview'),
      key: 'coverUrl',
      width: 100,
      render: (row) => {
        const src = resolveActivitySrc(row.coverUrl)
        return src
          ? h(NImage, {
              src,
              width: 72,
              height: 40,
              objectFit: 'cover',
              style: 'border-radius: 4px',
            })
          : '-'
      },
    },
    { title: t('activity.title'), key: 'title', ellipsis: { tooltip: true } },
    { title: t('activity.sortOrder'), key: 'sortOrder', width: 80 },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) =>
        h(NTag, { type: statusType(row.status), size: 'small' }, () => statusLabel(row.status)),
    },
    {
      title: t('activity.window'),
      key: 'window',
      width: 200,
      render: (row) => {
        const start = formatTime(row.startAt)
        const end = formatTime(row.endAt)
        if (start === '-' && end === '-') return t('activity.always')
        return `${start} ~ ${end}`
      },
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
      width: 260,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          row.status !== 'published' && auth.hasPermission('admin:activity:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          row.status !== 'published' && auth.hasPermission('admin:activity:publish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'success',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('activity.publishTitle'),
                      content: t('activity.publishConfirm', { title: row.title || row.id }),
                      positiveText: t('activity.publish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await publishActivity(row.id)
                        message.success(t('activity.publishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('activity.publish')
              )
            : null,
          row.status === 'published' && auth.hasPermission('admin:activity:unpublish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'warning',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('activity.unpublishTitle'),
                      content: t('activity.unpublishConfirm', { title: row.title || row.id }),
                      positiveText: t('activity.unpublish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await unpublishActivity(row.id)
                        message.success(t('activity.unpublishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('activity.unpublish')
              )
            : null,
          row.status !== 'published' && auth.hasPermission('admin:activity:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('activity.deleteTitle'),
                      content: t('activity.deleteConfirm', { title: row.title || row.id }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteActivity(row.id)
                        message.success(t('activity.deleteSuccess'))
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

function openCreate() {
  editing.value = null
  Object.assign(form, {
    title: '',
    coverKey: '',
    previewUrl: '',
    linkUrl: '',
    description: '',
    sortOrder: 0,
    startAt: null,
    endAt: null,
  })
  showForm.value = true
}

function openEdit(row: ActivityItem) {
  editing.value = row
  Object.assign(form, {
    title: row.title || '',
    coverKey: row.coverKey || '',
    previewUrl: resolveActivitySrc(row.coverUrl),
    linkUrl: row.linkUrl || '',
    description: row.description || '',
    sortOrder: row.sortOrder ?? 0,
    startAt: toMillis(row.startAt),
    endAt: toMillis(row.endAt),
  })
  showForm.value = true
}

function pickImage() {
  if (imageUploading.value) return
  imageInputRef.value?.click()
}

async function onImageSelected(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.error(t('activity.imageTypeInvalid'))
    return
  }
  const localPreview = URL.createObjectURL(file)
  form.previewUrl = localPreview
  imageUploading.value = true
  try {
    const result = await uploadActivityCover(file)
    form.coverKey = result.objectKey
    if (result.url) {
      URL.revokeObjectURL(localPreview)
      form.previewUrl = result.url
    }
    message.success(t('activity.uploadSuccess'))
  } catch {
    URL.revokeObjectURL(localPreview)
    form.previewUrl = editing.value ? resolveActivitySrc(editing.value.coverUrl) : ''
    if (!editing.value) form.coverKey = ''
  } finally {
    imageUploading.value = false
  }
}

async function submitForm() {
  await formRef.value?.validate()
  if (!form.coverKey.trim()) {
    message.error(t('activity.coverRequired'))
    return
  }
  saving.value = true
  try {
    const payload: ActivityPayload = {
      title: form.title.trim(),
      coverUrl: form.coverKey.trim(),
      linkUrl: form.linkUrl.trim() || null,
      description: form.description.trim() || null,
      sortOrder: form.sortOrder ?? 0,
      startAt: form.startAt,
      endAt: form.endAt,
    }
    if (editing.value) {
      await updateActivity(editing.value.id, payload)
      message.success(t('activity.updateSuccess'))
    } else {
      await createActivity(payload)
      message.success(t('activity.createSuccess'))
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
    const data = await listActivities({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      activityStatus: query.activityStatus || undefined,
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
            :placeholder="t('activity.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.activityStatus"
            :options="statusOptions"
            style="width: 140px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:activity:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('activity.create') }}
        </NButton>
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

    <NModal
      v-model:show="showForm"
      preset="card"
      :title="editing ? t('activity.editTitle') : t('activity.createTitle')"
      style="width: 560px"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="90">
        <NFormItem :label="t('activity.title')" path="title">
          <NInput v-model:value="form.title" maxlength="128" show-count />
        </NFormItem>
        <NFormItem :label="t('activity.cover')" path="coverKey">
          <div class="ops-upload-wrap">
            <button
              type="button"
              class="ops-upload"
              :class="{ uploading: imageUploading }"
              :disabled="imageUploading"
              @click="pickImage"
            >
              <NSpin v-if="imageUploading" size="small" />
              <img
                v-else-if="form.previewUrl"
                class="ops-upload-img"
                :src="form.previewUrl"
                alt=""
              />
              <span v-else class="ops-upload-placeholder">{{ t('activity.uploadHint') }}</span>
              <span class="ops-upload-mask">
                {{ imageUploading ? t('activity.uploading') : t('activity.changeImage') }}
              </span>
            </button>
            <input
              ref="imageInputRef"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              hidden
              @change="onImageSelected"
            />
          </div>
        </NFormItem>
        <NFormItem :label="t('activity.description')" path="description">
          <NInput
            v-model:value="form.description"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-count
          />
        </NFormItem>
        <NFormItem :label="t('activity.linkUrl')" path="linkUrl">
          <NInput
            v-model:value="form.linkUrl"
            :placeholder="t('activity.linkPlaceholder')"
            maxlength="1024"
          />
        </NFormItem>
        <NFormItem :label="t('activity.sortOrder')" path="sortOrder">
          <NInputNumber v-model:value="form.sortOrder" :min="0" style="width: 160px" />
        </NFormItem>
        <NFormItem :label="t('activity.startAt')" path="startAt">
          <NDatePicker v-model:value="form.startAt" type="datetime" clearable style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('activity.endAt')" path="endAt">
          <NDatePicker v-model:value="form.endAt" type="datetime" clearable style="width: 100%" />
        </NFormItem>
        <div class="ops-schedule-hint">{{ t('activity.scheduleHint') }}</div>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" @click="submitForm">{{
            t('common.save')
          }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.ops-upload-wrap {
  width: 100%;
}
.ops-upload {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 320px;
  height: 120px;
  padding: 0;
  border: 1px dashed var(--n-border-color);
  border-radius: 8px;
  background: var(--n-color);
  cursor: pointer;
  overflow: hidden;
}
.ops-upload:disabled {
  cursor: wait;
}
.ops-upload-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ops-upload-placeholder {
  color: var(--n-text-color-3);
  font-size: 13px;
}
.ops-upload-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.ops-upload:hover .ops-upload-mask,
.ops-upload.uploading .ops-upload-mask {
  opacity: 1;
}
.ops-schedule-hint {
  margin: -4px 0 8px 90px;
  font-size: 12px;
  color: var(--n-text-color-3);
  line-height: 1.4;
}
</style>
