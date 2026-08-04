<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
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
  createRecommend,
  deleteRecommend,
  listRecommends,
  publishRecommend,
  unpublishRecommend,
  updateRecommend,
  uploadRecommendImage,
  type RecommendItem,
  type RecommendPayload,
  type RecommendSlot,
} from '@/api/recommends'
import { formatTime } from '@/utils/format'
import { resolveRecommendSrc } from '@/utils/mediaUrl'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<RecommendItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  recommendStatus: '',
  slotCode: '' as RecommendSlot | '',
})

const showForm = ref(false)
const editing = ref<RecommendItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)
const form = reactive<{
  title: string
  subtitle: string
  imageKey: string
  previewUrl: string
  linkUrl: string
  slotCode: RecommendSlot
  sortOrder: number
  startAt: number | null
  endAt: number | null
}>({
  title: '',
  subtitle: '',
  imageKey: '',
  previewUrl: '',
  linkUrl: '',
  slotCode: 'discover',
  sortOrder: 0,
  startAt: null,
  endAt: null,
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('recommend.draft'), value: 'draft' },
    { label: t('recommend.published'), value: 'published' },
    { label: t('recommend.unpublished'), value: 'unpublished' },
  ]
})

const slotFilterOptions = computed(() => {
  void locale.value
  return [
    { label: t('recommend.allSlots'), value: '' },
    { label: t('recommend.slotDiscover'), value: 'discover' },
    { label: t('recommend.slotChatSidebar'), value: 'chat_sidebar' },
    { label: t('recommend.slotMoments'), value: 'moments' },
  ]
})

const slotOptions = computed(() => {
  void locale.value
  return [
    { label: t('recommend.slotDiscover'), value: 'discover' },
    { label: t('recommend.slotChatSidebar'), value: 'chat_sidebar' },
    { label: t('recommend.slotMoments'), value: 'moments' },
  ]
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    title: { required: true, message: t('recommend.titleRequired'), trigger: ['blur', 'input'] },
    imageKey: {
      required: true,
      message: t('recommend.imageRequired'),
      trigger: ['change', 'blur'],
      validator: () => {
        if (!form.imageKey.trim()) return new Error(t('recommend.imageRequired'))
        return true
      },
    },
    slotCode: { required: true, message: t('recommend.slotRequired'), trigger: ['change', 'blur'] },
  }
})

function statusLabel(status?: string) {
  const label: Record<string, string> = {
    draft: t('recommend.draft'),
    published: t('recommend.published'),
    unpublished: t('recommend.unpublished'),
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

function slotLabel(slot?: string) {
  if (slot === 'discover') return t('recommend.slotDiscover')
  if (slot === 'chat_sidebar') return t('recommend.slotChatSidebar')
  if (slot === 'moments') return t('recommend.slotMoments')
  return slot || '-'
}

function toMillis(value?: string | number | Date | null): number | null {
  if (value == null || value === '') return null
  const d = dayjs(value)
  return d.isValid() ? d.valueOf() : null
}

const columns = computed<DataTableColumns<RecommendItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('recommend.preview'),
      key: 'imageUrl',
      width: 100,
      render: (row) => {
        const src = resolveRecommendSrc(row.imageUrl)
        return src
          ? h(NImage, {
              src,
              width: 72,
              height: 40,
              objectFit: 'cover',
              style: 'border-radius: var(--lx-radius)',
            })
          : '-'
      },
    },
    { title: t('recommend.title'), key: 'title', ellipsis: { tooltip: true } },
    {
      title: t('recommend.slot'),
      key: 'slotCode',
      width: 110,
      render: (row) => slotLabel(row.slotCode),
    },
    { title: t('recommend.sortOrder'), key: 'sortOrder', width: 80 },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) =>
        h(NTag, { type: statusType(row.status), size: 'small' }, () => statusLabel(row.status)),
    },
    {
      title: t('recommend.window'),
      key: 'window',
      width: 200,
      render: (row) => {
        const start = formatTime(row.startAt)
        const end = formatTime(row.endAt)
        if (start === '-' && end === '-') return t('recommend.always')
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
          row.status !== 'published' && auth.hasPermission('admin:recommend:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          row.status !== 'published' && auth.hasPermission('admin:recommend:publish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'success',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('recommend.publishTitle'),
                      content: t('recommend.publishConfirm', { title: row.title || row.id }),
                      positiveText: t('recommend.publish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await publishRecommend(row.id)
                        message.success(t('recommend.publishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('recommend.publish')
              )
            : null,
          row.status === 'published' && auth.hasPermission('admin:recommend:unpublish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'warning',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('recommend.unpublishTitle'),
                      content: t('recommend.unpublishConfirm', { title: row.title || row.id }),
                      positiveText: t('recommend.unpublish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await unpublishRecommend(row.id)
                        message.success(t('recommend.unpublishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('recommend.unpublish')
              )
            : null,
          row.status !== 'published' && auth.hasPermission('admin:recommend:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('recommend.deleteTitle'),
                      content: t('recommend.deleteConfirm', { title: row.title || row.id }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteRecommend(row.id)
                        message.success(t('recommend.deleteSuccess'))
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
    subtitle: '',
    imageKey: '',
    previewUrl: '',
    linkUrl: '',
    slotCode: 'discover' as RecommendSlot,
    sortOrder: 0,
    startAt: null,
    endAt: null,
  })
  showForm.value = true
}

function openEdit(row: RecommendItem) {
  editing.value = row
  const slot = (
    ['discover', 'chat_sidebar', 'moments'].includes(String(row.slotCode))
      ? row.slotCode
      : 'discover'
  ) as RecommendSlot
  Object.assign(form, {
    title: row.title || '',
    subtitle: row.subtitle || '',
    imageKey: row.imageKey || '',
    previewUrl: resolveRecommendSrc(row.imageUrl),
    linkUrl: row.linkUrl || '',
    slotCode: slot,
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
    message.error(t('recommend.imageTypeInvalid'))
    return
  }
  const localPreview = URL.createObjectURL(file)
  form.previewUrl = localPreview
  imageUploading.value = true
  try {
    const result = await uploadRecommendImage(file)
    form.imageKey = result.objectKey
    if (result.url) {
      URL.revokeObjectURL(localPreview)
      form.previewUrl = result.url
    }
    message.success(t('recommend.uploadSuccess'))
  } catch {
    URL.revokeObjectURL(localPreview)
    form.previewUrl = editing.value ? resolveRecommendSrc(editing.value.imageUrl) : ''
    if (!editing.value) form.imageKey = ''
  } finally {
    imageUploading.value = false
  }
}

async function submitForm() {
  await formRef.value?.validate()
  if (!form.imageKey.trim()) {
    message.error(t('recommend.imageRequired'))
    return
  }
  saving.value = true
  try {
    const payload: RecommendPayload = {
      slotCode: form.slotCode,
      title: form.title.trim(),
      subtitle: form.subtitle.trim() || null,
      imageUrl: form.imageKey.trim(),
      linkUrl: form.linkUrl.trim() || null,
      sortOrder: form.sortOrder ?? 0,
      startAt: form.startAt,
      endAt: form.endAt,
    }
    if (editing.value) {
      await updateRecommend(editing.value.id, payload)
      message.success(t('recommend.updateSuccess'))
    } else {
      await createRecommend(payload)
      message.success(t('recommend.createSuccess'))
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
    const data = await listRecommends({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      recommendStatus: query.recommendStatus || undefined,
      slotCode: query.slotCode || undefined,
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
            :placeholder="t('recommend.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.recommendStatus"
            :options="statusOptions"
            style="width: 140px"
          />
          <NSelect
            v-model:value="query.slotCode"
            :options="slotFilterOptions"
            style="width: 150px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:recommend:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('recommend.create') }}
        </NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="1200"
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
      
      :title="editing ? t('recommend.editTitle') : t('recommend.createTitle')"
      
     :width="560">
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="90">
        <NFormItem :label="t('recommend.title')" path="title">
          <NInput v-model:value="form.title" maxlength="128" show-count />
        </NFormItem>
        <NFormItem :label="t('recommend.subtitle')" path="subtitle">
          <NInput v-model:value="form.subtitle" maxlength="255" />
        </NFormItem>
        <NFormItem :label="t('recommend.image')" path="imageKey">
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
              <span v-else class="ops-upload-placeholder">{{ t('recommend.uploadHint') }}</span>
              <span class="ops-upload-mask">
                {{ imageUploading ? t('recommend.uploading') : t('recommend.changeImage') }}
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
        <NFormItem :label="t('recommend.linkUrl')" path="linkUrl">
          <NInput
            v-model:value="form.linkUrl"
            :placeholder="t('recommend.linkPlaceholder')"
            maxlength="1024"
          />
        </NFormItem>
        <NFormItem :label="t('recommend.slot')" path="slotCode">
          <NSelect v-model:value="form.slotCode" :options="slotOptions" />
        </NFormItem>
        <NFormItem :label="t('recommend.sortOrder')" path="sortOrder">
          <NInputNumber v-model:value="form.sortOrder" :min="0" style="width: 160px" />
        </NFormItem>
        <NFormItem :label="t('recommend.startAt')" path="startAt">
          <NDatePicker v-model:value="form.startAt" type="datetime" clearable style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('recommend.endAt')" path="endAt">
          <NDatePicker v-model:value="form.endAt" type="datetime" clearable style="width: 100%" />
        </NFormItem>
        <div class="ops-schedule-hint">{{ t('recommend.scheduleHint') }}</div>
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
  border-radius: var(--lx-radius);
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
