<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
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
  createBanner,
  deleteBanner,
  listBanners,
  publishBanner,
  unpublishBanner,
  updateBanner,
  uploadBannerImage,
  type BannerItem,
  type BannerPayload,
  type BannerPosition,
} from '@/api/banners'
import { formatTime } from '@/utils/format'
import { resolveBannerSrc } from '@/utils/mediaUrl'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<BannerItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  bannerStatus: '',
  position: '' as BannerPosition | '',
})

const showForm = ref(false)
const editing = ref<BannerItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)
const form = reactive<{
  title: string
  imageKey: string
  previewUrl: string
  linkUrl: string
  position: BannerPosition
  sortOrder: number
  startAt: number | null
  endAt: number | null
}>({
  title: '',
  imageKey: '',
  previewUrl: '',
  linkUrl: '',
  position: 'home',
  sortOrder: 0,
  startAt: null,
  endAt: null,
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('banner.draft'), value: 'draft' },
    { label: t('banner.published'), value: 'published' },
    { label: t('banner.unpublished'), value: 'unpublished' },
  ]
})

const positionFilterOptions = computed(() => {
  void locale.value
  return [
    { label: t('banner.allPositions'), value: '' },
    { label: t('banner.posHome'), value: 'home' },
    { label: t('banner.posLogin'), value: 'login' },
  ]
})

const positionOptions = computed(() => {
  void locale.value
  return [
    { label: t('banner.posHome'), value: 'home' },
    { label: t('banner.posLogin'), value: 'login' },
  ]
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    title: { required: true, message: t('banner.titleRequired'), trigger: ['blur', 'input'] },
    imageKey: {
      required: true,
      message: t('banner.imageRequired'),
      trigger: ['change', 'blur'],
      validator: () => {
        if (!form.imageKey.trim()) {
          return new Error(t('banner.imageRequired'))
        }
        return true
      },
    },
    position: {
      required: true,
      message: t('banner.positionRequired'),
      trigger: ['change', 'blur'],
    },
  }
})

function statusLabel(status?: string) {
  const label: Record<string, string> = {
    draft: t('banner.draft'),
    published: t('banner.published'),
    unpublished: t('banner.unpublished'),
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

/** 已发布但当前不在时效窗内 → 客户端不可见 */
function isScheduleExpired(row: BannerItem): boolean {
  if (row.status !== 'published') return false
  const now = Date.now()
  const start = toMillis(row.startAt)
  const end = toMillis(row.endAt)
  if (start != null && now < start) return false // 未开始，不算过期
  if (end != null && now > end) return true
  return false
}

function isSchedulePending(row: BannerItem): boolean {
  if (row.status !== 'published') return false
  const start = toMillis(row.startAt)
  return start != null && Date.now() < start
}

function positionLabel(position?: string) {
  if (position === 'home') return t('banner.posHome')
  if (position === 'login') return t('banner.posLogin')
  return position || '-'
}

function toMillis(value?: string | number | Date | null): number | null {
  if (value == null || value === '') return null
  const d = dayjs(value)
  return d.isValid() ? d.valueOf() : null
}

function displayImage(row: BannerItem) {
  return resolveBannerSrc(row.imageUrl)
}

const columns = computed<DataTableColumns<BannerItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('banner.preview'),
      key: 'imageUrl',
      width: 100,
      render: (row) => {
        const src = displayImage(row)
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
    { title: t('banner.title'), key: 'title', ellipsis: { tooltip: true } },
    {
      title: t('banner.position'),
      key: 'position',
      width: 90,
      render: (row) => positionLabel(row.position),
    },
    {
      title: t('banner.sortOrder'),
      key: 'sortOrder',
      width: 80,
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 140,
      render: (row) => {
        const tags = [
          h(NTag, { type: statusType(row.status), size: 'small' }, () => statusLabel(row.status)),
        ]
        if (isScheduleExpired(row)) {
          tags.push(
            h(NTag, { type: 'error', size: 'small', bordered: false }, () => t('banner.expired'))
          )
        } else if (isSchedulePending(row)) {
          tags.push(
            h(NTag, { type: 'info', size: 'small', bordered: false }, () => t('banner.pending'))
          )
        }
        return h(NSpace, { size: 4 }, () => tags)
      },
    },
    {
      title: t('banner.window'),
      key: 'window',
      width: 200,
      render: (row) => {
        const start = formatTime(row.startAt)
        const end = formatTime(row.endAt)
        if (start === '-' && end === '-') return t('banner.always')
        const text = `${start} ~ ${end}`
        if (isScheduleExpired(row)) {
          return h('span', { style: 'color: var(--n-error-color)' }, text)
        }
        return text
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
          row.status !== 'published' && auth.hasPermission('admin:banner:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          row.status !== 'published' && auth.hasPermission('admin:banner:publish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'success',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('banner.publishTitle'),
                      content: t('banner.publishConfirm', { title: row.title }),
                      positiveText: t('banner.publish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await publishBanner(row.id)
                        message.success(t('banner.publishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('banner.publish')
              )
            : null,
          row.status === 'published' && auth.hasPermission('admin:banner:unpublish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'warning',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('banner.unpublishTitle'),
                      content: t('banner.unpublishConfirm', { title: row.title }),
                      positiveText: t('banner.unpublish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await unpublishBanner(row.id)
                        message.success(t('banner.unpublishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('banner.unpublish')
              )
            : null,
          row.status !== 'published' && auth.hasPermission('admin:banner:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('banner.deleteTitle'),
                      content: t('banner.deleteConfirm', { title: row.title }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteBanner(row.id)
                        message.success(t('banner.deleteSuccess'))
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
    imageKey: '',
    previewUrl: '',
    linkUrl: '',
    position: 'home' as BannerPosition,
    sortOrder: 0,
    startAt: null,
    endAt: null,
  })
  showForm.value = true
}

function openEdit(row: BannerItem) {
  editing.value = row
  Object.assign(form, {
    title: row.title,
    imageKey: row.imageKey || '',
    previewUrl: resolveBannerSrc(row.imageUrl),
    linkUrl: row.linkUrl || '',
    position: row.position === 'login' ? 'login' : 'home',
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
    message.error(t('banner.imageTypeInvalid'))
    return
  }
  const localPreview = URL.createObjectURL(file)
  form.previewUrl = localPreview
  imageUploading.value = true
  try {
    const result = await uploadBannerImage(file)
    form.imageKey = result.objectKey
    if (result.url) {
      URL.revokeObjectURL(localPreview)
      form.previewUrl = result.url
    }
    message.success(t('banner.uploadSuccess'))
  } catch {
    URL.revokeObjectURL(localPreview)
    form.previewUrl = editing.value ? resolveBannerSrc(editing.value.imageUrl) : ''
    if (!editing.value) form.imageKey = ''
  } finally {
    imageUploading.value = false
  }
}

async function submitForm() {
  await formRef.value?.validate()
  if (!form.imageKey.trim()) {
    message.error(t('banner.imageRequired'))
    return
  }
  saving.value = true
  try {
    const payload: BannerPayload = {
      title: form.title.trim(),
      imageUrl: form.imageKey.trim(),
      linkUrl: form.linkUrl.trim() || null,
      position: form.position,
      sortOrder: form.sortOrder ?? 0,
      startAt: form.startAt,
      endAt: form.endAt,
    }
    if (editing.value) {
      await updateBanner(editing.value.id, payload)
      message.success(t('banner.updateSuccess'))
    } else {
      await createBanner(payload)
      message.success(t('banner.createSuccess'))
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
    const data = await listBanners({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      bannerStatus: query.bannerStatus || undefined,
      position: query.position || undefined,
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
      <NAlert type="info" :bordered="false" class="banner-client-hint" style="margin-bottom: 12px">
        {{ t('banner.clientDisabledHint') }}
      </NAlert>
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('banner.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.bannerStatus"
            :options="statusOptions"
            style="width: 140px"
          />
          <NSelect
            v-model:value="query.position"
            :options="positionFilterOptions"
            style="width: 140px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:banner:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('banner.create') }}
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

    <NModal
      v-model:show="showForm"
      preset="card"
      :title="editing ? t('banner.editTitle') : t('banner.createTitle')"
      style="width: 560px"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="90">
        <NFormItem :label="t('banner.title')" path="title">
          <NInput
            v-model:value="form.title"
            :placeholder="t('banner.titlePlaceholder')"
            maxlength="128"
            show-count
          />
        </NFormItem>
        <NFormItem :label="t('banner.image')" path="imageKey">
          <div class="banner-upload-wrap">
            <button
              type="button"
              class="banner-upload"
              :class="{ uploading: imageUploading }"
              :disabled="imageUploading"
              :title="t('banner.uploadHint')"
              @click="pickImage"
            >
              <NSpin v-if="imageUploading" size="small" />
              <img
                v-else-if="form.previewUrl"
                class="banner-upload-img"
                :src="form.previewUrl"
                alt=""
              />
              <span v-else class="banner-upload-placeholder">{{ t('banner.uploadHint') }}</span>
              <span class="banner-upload-mask">
                {{ imageUploading ? t('banner.uploading') : t('banner.changeImage') }}
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
        <NFormItem :label="t('banner.linkUrl')" path="linkUrl">
          <NInput
            v-model:value="form.linkUrl"
            :placeholder="t('banner.linkPlaceholder')"
            maxlength="1024"
          />
        </NFormItem>
        <NFormItem :label="t('banner.position')" path="position">
          <NSelect v-model:value="form.position" :options="positionOptions" />
        </NFormItem>
        <NFormItem :label="t('banner.sortOrder')" path="sortOrder">
          <NInputNumber v-model:value="form.sortOrder" :min="0" style="width: 160px" />
        </NFormItem>
        <NFormItem :label="t('banner.startAt')" path="startAt">
          <NDatePicker v-model:value="form.startAt" type="datetime" clearable style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('banner.endAt')" path="endAt">
          <NDatePicker v-model:value="form.endAt" type="datetime" clearable style="width: 100%" />
        </NFormItem>
        <div class="banner-schedule-hint">{{ t('banner.scheduleHint') }}</div>
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
.banner-upload-wrap {
  width: 100%;
}
.banner-upload {
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
.banner-upload:disabled {
  cursor: wait;
}
.banner-upload-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.banner-upload-placeholder {
  color: var(--n-text-color-3);
  font-size: 13px;
}
.banner-upload-mask {
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
.banner-upload:hover .banner-upload-mask,
.banner-upload.uploading .banner-upload-mask,
.banner-upload:focus-visible .banner-upload-mask {
  opacity: 1;
}
.banner-schedule-hint {
  margin: -4px 0 8px 90px;
  font-size: 12px;
  color: var(--n-text-color-3);
  line-height: 1.4;
}
</style>
