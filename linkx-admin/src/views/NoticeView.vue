<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import {
  createNotice,
  deleteNotice,
  listNotices,
  publishNotice,
  unpublishNotice,
  updateNotice,
  type NoticeItem,
  type NoticePayload,
  type NoticeTargetSide,
} from '@/api/notices'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'
import { onAdminRealtimeEvent } from '@/api/realtime'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<NoticeItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  noticeStatus: '',
  targetSide: 'admin' as NoticeTargetSide,
})

const showForm = ref(false)
const showDetail = ref(false)
const editing = ref<NoticeItem | null>(null)
const detailItem = ref<NoticeItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const form = reactive<NoticePayload>({
  title: '',
  content: '',
  targetSide: 'admin',
})

let offRealtime: (() => void) | null = null

const sideTabs = computed(() => {
  void locale.value
  return [
    { label: t('notice.sideAdmin'), value: 'admin' as const },
    { label: t('notice.sideClient'), value: 'client' as const },
  ]
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('notice.draft'), value: 'draft' },
    { label: t('notice.published'), value: 'published' },
    { label: t('notice.unpublished'), value: 'unpublished' },
  ]
})

const targetOptions = computed(() => {
  void locale.value
  return [
    { label: t('notice.sideAdmin'), value: 'admin' },
    { label: t('notice.sideClient'), value: 'client' },
  ]
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    title: { required: true, message: t('notice.titleRequired'), trigger: ['blur', 'input'] },
    content: { required: true, message: t('notice.contentRequired'), trigger: ['blur', 'input'] },
    targetSide: { required: true, message: t('notice.sideRequired'), trigger: ['change', 'blur'] },
  }
})

function statusLabel(status?: string) {
  const label: Record<string, string> = {
    draft: t('notice.draft'),
    published: t('notice.published'),
    unpublished: t('notice.unpublished'),
  }
  return label[status || ''] || status || '-'
}

function statusType(status?: string): 'default' | 'success' | 'warning' {
  const map: Record<string, 'default' | 'success' | 'warning'> = {
    draft: 'default',
    published: 'success',
    unpublished: 'warning',
  }
  return map[status || ''] || 'default'
}

function statusTag(status?: string) {
  return h(NTag, { type: statusType(status), size: 'small' }, () => statusLabel(status))
}

function sideLabel(side?: string) {
  if (side === 'admin') return t('notice.sideAdmin')
  if (side === 'client') return t('notice.sideClient')
  return side || '-'
}

function sideTag(side?: string) {
  return h(
    NTag,
    { type: side === 'admin' ? 'info' : 'success', size: 'small', bordered: false },
    () => sideLabel(side)
  )
}

function publishConfirmText(row: NoticeItem) {
  if (row.targetSide === 'admin') {
    return t('notice.publishConfirmAdmin', { title: row.title })
  }
  return t('notice.publishConfirmClient', { title: row.title })
}

function unpublishConfirmText(row: NoticeItem) {
  if ((row.targetSide || query.targetSide) === 'admin') {
    return t('notice.unpublishConfirmAdmin', { title: row.title })
  }
  return t('notice.unpublishConfirmClient', { title: row.title })
}

const columns = computed<DataTableColumns<NoticeItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 90 },
    { title: t('notice.title'), key: 'title', ellipsis: { tooltip: true } },
    {
      title: t('notice.targetSide'),
      key: 'targetSide',
      width: 100,
      render: (row) => sideTag(row.targetSide),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) => statusTag(row.status),
    },
    {
      title: t('notice.publishedAt'),
      key: 'publishedAt',
      width: 170,
      render: (row) => formatTime(row.publishedAt),
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
      width: 280,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          auth.hasPermission('admin:notice:view')
            ? h(NButton, { size: 'tiny', onClick: () => openDetail(row) }, () => t('common.detail'))
            : null,
          row.status !== 'published' && auth.hasPermission('admin:notice:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          row.status !== 'published' && auth.hasPermission('admin:notice:publish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'success',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('notice.publishTitle'),
                      content: publishConfirmText(row),
                      positiveText: t('notice.publish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await publishNotice(row.id)
                        message.success(
                          row.targetSide === 'admin'
                            ? t('notice.publishSuccessAdmin')
                            : t('notice.publishSuccessClient')
                        )
                        await load()
                      },
                    }),
                },
                () => t('notice.publish')
              )
            : null,
          row.status === 'published' && auth.hasPermission('admin:notice:unpublish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'warning',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('notice.unpublishTitle'),
                      content: unpublishConfirmText(row),
                      positiveText: t('notice.unpublish'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await unpublishNotice(row.id)
                        message.success(t('notice.unpublishSuccess'))
                        await load()
                      },
                    }),
                },
                () => t('notice.unpublish')
              )
            : null,
          row.status !== 'published' && auth.hasPermission('admin:notice:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () =>
                    dialog.warning({
                      title: t('notice.deleteTitle'),
                      content: t('notice.deleteConfirm', { title: row.title }),
                      positiveText: t('common.delete'),
                      negativeText: t('common.cancel'),
                      onPositiveClick: async () => {
                        await deleteNotice(row.id)
                        message.success(t('notice.deleteSuccess'))
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
    content: '',
    targetSide: query.targetSide || 'admin',
  })
  showForm.value = true
}

function openEdit(row: NoticeItem) {
  editing.value = row
  Object.assign(form, {
    title: row.title,
    content: row.content,
    targetSide: row.targetSide === 'client' ? 'client' : 'admin',
  })
  showForm.value = true
}

function openDetail(row: NoticeItem) {
  detailItem.value = row
  showDetail.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: NoticePayload = {
      title: form.title.trim(),
      content: form.content.trim(),
      targetSide: form.targetSide,
    }
    if (editing.value) {
      await updateNotice(editing.value.id, payload)
      message.success(t('notice.updateSuccess'))
    } else {
      await createNotice(payload)
      message.success(t('notice.createSuccess'))
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
    const data = await listNotices({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      noticeStatus: query.noticeStatus || undefined,
      targetSide: query.targetSide || undefined,
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

function switchSide(side: NoticeTargetSide) {
  if (query.targetSide === side) return
  query.targetSide = side
  query.page = 1
  void load()
}

onMounted(() => {
  void load()
  offRealtime = onAdminRealtimeEvent((evt) => {
    if (!evt?.type) return
    const type = String(evt.type)
    // 列表协同刷新；管理端公告横幅由 Layout 处理
    if (
      type.startsWith('notice_') ||
      type === 'admin_notice_published' ||
      type === 'admin_notice_unpublished'
    ) {
      void load()
    }
  })
})

onUnmounted(() => {
  offRealtime?.()
  offRealtime = null
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <div class="side-tabs">
        <NRadioGroup :value="query.targetSide" size="medium" @update:value="switchSide">
          <NRadioButton
            v-for="tab in sideTabs"
            :key="tab.value"
            :value="tab.value"
            :label="tab.label"
          />
        </NRadioGroup>
        <span class="side-hint">{{
          query.targetSide === 'admin' ? t('notice.sideAdminHint') : t('notice.sideClientHint')
        }}</span>
      </div>
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('notice.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect
            v-model:value="query.noticeStatus"
            :options="statusOptions"
            style="width: 140px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:notice:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('notice.create') }}
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
      :title="editing ? t('notice.editTitle') : t('notice.createTitle')"
      style="width: 560px"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="80">
        <NFormItem :label="t('notice.targetSide')" path="targetSide">
          <NRadioGroup
            v-model:value="form.targetSide"
            :disabled="!!editing && editing.status === 'published'"
          >
            <NRadioButton
              v-for="opt in targetOptions"
              :key="opt.value"
              :value="opt.value"
              :label="opt.label"
            />
          </NRadioGroup>
        </NFormItem>
        <NFormItem :label="t('notice.title')" path="title">
          <NInput
            v-model:value="form.title"
            :placeholder="t('notice.titlePlaceholder')"
            maxlength="128"
            show-count
          />
        </NFormItem>
        <NFormItem :label="t('notice.content')" path="content">
          <NInput
            v-model:value="form.content"
            type="textarea"
            :placeholder="t('notice.contentPlaceholder')"
            :rows="8"
            maxlength="20000"
            show-count
          />
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
    </NModal>

    <NModal
      v-model:show="showDetail"
      preset="card"
      :title="t('notice.detailTitle')"
      style="width: 560px"
    >
      <template v-if="detailItem">
        <div class="notice-detail">
          <div class="notice-detail__row">
            <span class="notice-detail__label">{{ t('notice.title') }}</span>
            <span>{{ detailItem.title }}</span>
          </div>
          <div class="notice-detail__row">
            <span class="notice-detail__label">{{ t('notice.targetSide') }}</span>
            <NTag :type="detailItem.targetSide === 'admin' ? 'info' : 'success'" size="small">
              {{ sideLabel(detailItem.targetSide) }}
            </NTag>
          </div>
          <div class="notice-detail__row">
            <span class="notice-detail__label">{{ t('common.status') }}</span>
            <NTag :type="statusType(detailItem.status)" size="small">{{
              statusLabel(detailItem.status)
            }}</NTag>
          </div>
          <div class="notice-detail__row">
            <span class="notice-detail__label">{{ t('notice.publishedAt') }}</span>
            <span>{{ formatTime(detailItem.publishedAt) }}</span>
          </div>
          <div class="notice-detail__row notice-detail__content">
            <span class="notice-detail__label">{{ t('notice.content') }}</span>
            <pre>{{ detailItem.content }}</pre>
          </div>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.side-tabs {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.side-hint {
  font-size: 12px;
  color: var(--n-text-color-3);
}
.notice-detail__row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  line-height: 1.5;
}
.notice-detail__label {
  flex: 0 0 72px;
  color: var(--n-text-color-3);
}
.notice-detail__content {
  flex-direction: column;
}
.notice-detail__content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}
</style>
