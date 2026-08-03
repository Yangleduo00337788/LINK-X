<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import {
  createVersion,
  deleteVersion,
  listVersions,
  publishVersion,
  updateVersion,
  type VersionItem,
  type VersionPayload,
} from '@/api/versions'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const items = ref<VersionItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  versionStatus: '',
  channel: '',
})

const showForm = ref(false)
const editing = ref<VersionItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const form = reactive({
  version: '',
  channel: 'stable',
  releaseNotes: '',
  downloadUrl: '',
  forceUpdate: false,
  minSupportedVersion: '',
})

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('version.statusDraft'), value: 'draft' },
    { label: t('version.statusPublished'), value: 'published' },
    { label: t('version.statusArchived'), value: 'archived' },
  ]
})

const channelOptions = computed(() => {
  void locale.value
  return [
    { label: t('version.channelAll'), value: '' },
    { label: 'stable', value: 'stable' },
    { label: 'beta', value: 'beta' },
    { label: 'dev', value: 'dev' },
  ]
})

const channelFormOptions = computed(() => channelOptions.value.filter((o) => o.value))

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    version: { required: true, message: t('version.versionRequired'), trigger: ['blur', 'input'] },
    channel: { required: true, message: t('version.channelRequired'), trigger: ['blur', 'change'] },
  }
})

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: t('version.statusDraft'),
    published: t('version.statusPublished'),
    archived: t('version.statusArchived'),
  }
  return map[status || ''] || status || '-'
}

function statusType(status?: string): 'default' | 'success' | 'warning' | 'error' {
  const map: Record<string, 'default' | 'success' | 'warning' | 'error'> = {
    draft: 'default',
    published: 'success',
    archived: 'warning',
  }
  return map[status || ''] || 'default'
}

const columns = computed<DataTableColumns<VersionItem>>(() => {
  void locale.value
  return [
    { title: t('version.currentVersion'), key: 'version', width: 110 },
    { title: t('version.channel'), key: 'channel', width: 90 },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) =>
        h(NTag, { type: statusType(row.status), size: 'small' }, () => statusLabel(row.status)),
    },
    {
      title: t('setting.forceUpdate'),
      key: 'forceUpdate',
      width: 90,
      render: (row) => (row.forceUpdate ? t('common.yes') : t('common.no')),
    },
    {
      title: t('setting.minSupportedVersion'),
      key: 'minSupportedVersion',
      width: 130,
      ellipsis: { tooltip: true },
      render: (row) => row.minSupportedVersion || '-',
    },
    {
      title: t('version.publishedAt'),
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
      width: 220,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          row.status === 'draft' && auth.hasPermission('admin:version:edit')
            ? h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
            : null,
          row.status === 'draft' && auth.hasPermission('admin:version:publish')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'success',
                  secondary: true,
                  onClick: () => confirmPublish(row),
                },
                () => t('version.publish')
              )
            : null,
          row.status === 'draft' && auth.hasPermission('admin:version:delete')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  tertiary: true,
                  onClick: () => confirmDelete(row),
                },
                () => t('common.delete')
              )
            : null,
        ]),
    },
  ]
})

function resetForm() {
  form.version = ''
  form.channel = 'stable'
  form.releaseNotes = ''
  form.downloadUrl = ''
  form.forceUpdate = false
  form.minSupportedVersion = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  showForm.value = true
}

function openEdit(row: VersionItem) {
  editing.value = row
  form.version = row.version || ''
  form.channel = row.channel || 'stable'
  form.releaseNotes = row.releaseNotes || ''
  form.downloadUrl = row.downloadUrl || ''
  form.forceUpdate = !!row.forceUpdate
  form.minSupportedVersion = row.minSupportedVersion || ''
  showForm.value = true
}

function toPayload(): VersionPayload {
  return {
    version: form.version.trim(),
    channel: form.channel.trim(),
    releaseNotes: form.releaseNotes.trim(),
    downloadUrl: form.downloadUrl.trim(),
    forceUpdate: form.forceUpdate,
    minSupportedVersion: form.minSupportedVersion.trim(),
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listVersions({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      versionStatus: query.versionStatus || undefined,
      channel: query.channel || undefined,
    })
    items.value = res.items || []
    total.value = res.total || 0
  } catch (e) {
    message.error((e as Error).message || t('common.requestFailed'))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  void load()
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = toPayload()
    if (editing.value?.id) {
      await updateVersion(editing.value.id, payload)
      message.success(t('common.updateSuccess'))
    } else {
      await createVersion(payload)
      message.success(t('common.createSuccess'))
    }
    showForm.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || t('common.failed'))
  } finally {
    saving.value = false
  }
}

function confirmPublish(row: VersionItem) {
  dialog.warning({
    title: t('version.publishTitle'),
    content: t('version.publishConfirm', { version: row.version }),
    positiveText: t('version.publish'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await publishVersion(row.id)
        message.success(t('version.published'))
        await load()
      } catch (e) {
        message.error((e as Error).message || t('common.failed'))
      }
    },
  })
}

function confirmDelete(row: VersionItem) {
  dialog.warning({
    title: t('version.deleteTitle'),
    content: t('version.deleteConfirm', { version: row.version }),
    positiveText: t('common.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await deleteVersion(row.id)
        message.success(t('common.deleted'))
        await load()
      } catch (e) {
        message.error((e as Error).message || t('common.failed'))
      }
    },
  })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>{{ t('version.title') }}</h2>
      <NSpace>
        <NButton
          v-if="auth.hasPermission('admin:version:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('version.create') }}
        </NButton>
      </NSpace>
    </div>

    <NAlert type="info" :bordered="false" class="hint">
      {{ t('version.listHint') }}
    </NAlert>

    <div class="toolbar">
      <SearchAutoComplete
        v-model="query.keyword"
        :placeholder="t('common.search')"
        @search="onSearch"
      />
      <NSelect
        v-model:value="query.versionStatus"
        :options="statusOptions"
        :placeholder="t('common.status')"
        clearable
        style="width: 140px"
        @update:value="onSearch"
      />
      <NSelect
        v-model:value="query.channel"
        :options="channelOptions"
        :placeholder="t('version.channel')"
        clearable
        style="width: 120px"
        @update:value="onSearch"
      />
      <NButton @click="onSearch">{{ t('common.search') }}</NButton>
    </div>

    <NSpin :show="loading">
      <NDataTable
        :columns="columns"
        :data="items"
        :bordered="false"
        :single-line="false"
        :scroll-x="1100"
        :loading="loading"
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
    </NSpin>

    <NModal
      v-model:show="showForm"
      preset="card"
      :title="editing ? t('version.edit') : t('version.create')"
      style="width: 560px; max-width: 95vw"
      :mask-closable="false"
    >
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120">
        <NFormItem :label="t('version.currentVersion')" path="version">
          <NInput v-model:value="form.version" :placeholder="t('version.versionPh')" />
        </NFormItem>
        <NFormItem :label="t('version.channel')" path="channel">
          <NSelect v-model:value="form.channel" :options="channelFormOptions" />
        </NFormItem>
        <NFormItem :label="t('version.downloadUrl')">
          <NInput v-model:value="form.downloadUrl" :placeholder="t('version.downloadUrlPh')" />
        </NFormItem>
        <NFormItem :label="t('setting.minSupportedVersion')">
          <NInput v-model:value="form.minSupportedVersion" :placeholder="t('version.versionPh')" />
        </NFormItem>
        <NFormItem :label="t('setting.forceUpdate')">
          <NSwitch v-model:value="form.forceUpdate" />
        </NFormItem>
        <NFormItem :label="t('version.releaseNotes')">
          <NInput
            v-model:value="form.releaseNotes"
            type="textarea"
            :rows="4"
            :placeholder="t('version.releaseNotesPh')"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" @click="save">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.hint {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}
</style>
