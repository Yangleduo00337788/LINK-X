<!-- 作者：yangleduo -->
<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
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
  uploadVersionPackage,
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
  platform: '' as '' | 'windows' | 'macos' | 'linux',
})

const showForm = ref(false)
const formReadonly = ref(false)
const editing = ref<VersionItem | null>(null)
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const packageUploading = ref(false)
const packageInputRef = ref<HTMLInputElement | null>(null)
const form = reactive({
  version: '',
  channel: 'stable',
  platform: 'windows' as 'windows' | 'macos' | 'linux',
  releaseNotes: '',
  downloadUrl: '',
  packageFileName: '',
  packageSha256: '',
  packageSize: undefined as number | undefined,
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

const platformOptions = computed(() => {
  void locale.value
  return [
    { label: t('version.platformAll'), value: '' },
    { label: t('version.platformWindows'), value: 'windows' },
    { label: t('version.platformMacos'), value: 'macos' },
    { label: t('version.platformLinux'), value: 'linux' },
  ]
})

const platformFormOptions = computed(() => platformOptions.value.filter((o) => o.value))

const formTitle = computed(() => {
  if (formReadonly.value) return t('version.viewTitle')
  return editing.value ? t('version.edit') : t('version.create')
})

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    version: { required: true, message: t('version.versionRequired'), trigger: ['blur', 'input'] },
    channel: { required: true, message: t('version.channelRequired'), trigger: ['blur', 'change'] },
    platform: { required: true, message: t('version.platformRequired'), trigger: ['blur', 'change'] },
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

function resolveRowDownloadLink(row: VersionItem): string {
  return (row.downloadUrl || row.downloadKey || '').trim()
}

async function copyDownloadLink(row: VersionItem) {
  const link = resolveRowDownloadLink(row)
  if (!link) {
    message.warning(t('version.noDownloadLink'))
    return
  }
  try {
    await navigator.clipboard.writeText(link)
    message.success(t('version.linkCopied'))
  } catch {
    message.error(t('common.failed'))
  }
}

function openView(row: VersionItem) {
  formReadonly.value = true
  editing.value = row
  fillFormFromRow(row)
  showForm.value = true
}

function openEdit(row: VersionItem) {
  formReadonly.value = false
  editing.value = row
  fillFormFromRow(row)
  showForm.value = true
}

function fillFormFromRow(row: VersionItem) {
  form.version = row.version || ''
  form.channel = row.channel || 'stable'
  form.platform = (row.platform as 'windows' | 'macos' | 'linux') || 'windows'
  form.releaseNotes = row.releaseNotes || ''
  form.downloadUrl = row.downloadKey || row.downloadUrl || ''
  form.packageFileName =
    row.packageFileName || (row.downloadKey ? row.downloadKey.split('/').pop() || '' : '')
  form.packageSha256 = row.packageSha256 || ''
  form.packageSize = row.packageSize
  form.forceUpdate = !!row.forceUpdate
  form.minSupportedVersion = row.minSupportedVersion || ''
}

function duplicateAsDraft(row: VersionItem) {
  formReadonly.value = false
  editing.value = null
  fillFormFromRow(row)
  showForm.value = true
}

const columns = computed<DataTableColumns<VersionItem>>(() => {
  void locale.value
  return [
    { title: t('version.currentVersion'), key: 'version', width: 110 },
    { title: t('version.channel'), key: 'channel', width: 90 },
    { title: t('version.platform'), key: 'platform', width: 90 },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) =>
        h(NTag, { type: statusType(row.status), size: 'small' }, () => statusLabel(row.status)),
    },
    {
      title: t('version.downloadUrl'),
      key: 'downloadUrl',
      width: 160,
      ellipsis: { tooltip: true },
      render: (row) => row.downloadUrl || row.downloadKey || '-',
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
      width: 280,
      render: (row) =>
        h(NSpace, { size: 8 }, () => {
          const actions = []
          if (row.status === 'draft') {
            if (auth.hasPermission('admin:version:edit')) {
              actions.push(
                h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, () => t('common.edit'))
              )
            }
            if (auth.hasPermission('admin:version:publish')) {
              actions.push(
                h(
                  NButton,
                  {
                    size: 'tiny',
                    type: 'success',
                    secondary: true,
                    onClick: () => confirmPublish(row),
                  },
                  () => t('version.publish')
                )
              )
            }
            if (auth.hasPermission('admin:version:delete')) {
              actions.push(
                h(
                  NButton,
                  {
                    size: 'tiny',
                    type: 'error',
                    tertiary: true,
                    onClick: () => confirmDelete(row),
                  },
                  () => t('common.delete')
                )
              )
            }
          } else {
            actions.push(
              h(NButton, { size: 'tiny', onClick: () => openView(row) }, () => t('version.view'))
            )
            if (resolveRowDownloadLink(row)) {
              actions.push(
                h(
                  NButton,
                  { size: 'tiny', secondary: true, onClick: () => void copyDownloadLink(row) },
                  () => t('version.copyDownloadLink')
                )
              )
            }
            if (auth.hasPermission('admin:version:create')) {
              actions.push(
                h(
                  NButton,
                  { size: 'tiny', tertiary: true, onClick: () => duplicateAsDraft(row) },
                  () => t('version.duplicateDraft')
                )
              )
            }
          }
          return actions
        }),
    },
  ]
})

function resetForm() {
  form.version = ''
  form.channel = 'stable'
  form.platform = 'windows'
  form.releaseNotes = ''
  form.downloadUrl = ''
  form.packageFileName = ''
  form.packageSha256 = ''
  form.packageSize = undefined
  form.forceUpdate = false
  form.minSupportedVersion = ''
}

function detectPlatform(fileName: string): 'windows' | 'macos' | 'linux' {
  const lower = fileName.toLowerCase()
  if (lower.endsWith('.exe') || lower.endsWith('.msi')) return 'windows'
  if (lower.endsWith('.dmg')) return 'macos'
  return 'linux'
}

function pickPackage() {
  if (packageUploading.value) return
  packageInputRef.value?.click()
}

async function onPackageSelected(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const lower = file.name.toLowerCase()
  const allowed = ['.exe', '.msi', '.dmg', '.deb', '.rpm', '.appimage']
  if (!allowed.some((ext) => lower.endsWith(ext))) {
    message.error(t('version.packageTypeInvalid'))
    return
  }
  packageUploading.value = true
  try {
    const result = await uploadVersionPackage(file)
    form.downloadUrl = result.objectKey
    form.packageFileName = result.fileName || file.name
    form.packageSha256 = result.sha256 || ''
    form.packageSize = result.fileSize ?? file.size
    form.platform = detectPlatform(form.packageFileName)
    message.success(t('version.packageUploadSuccess'))
  } catch (e) {
    message.error((e as Error).message || t('version.packageUploadFail'))
  } finally {
    packageUploading.value = false
  }
}

function openCreate() {
  editing.value = null
  formReadonly.value = false
  resetForm()
  showForm.value = true
}

function toPayload(): VersionPayload {
  return {
    version: form.version.trim(),
    channel: form.channel.trim(),
    platform: form.platform,
    releaseNotes: form.releaseNotes.trim(),
    downloadUrl: form.downloadUrl.trim(),
    packageSha256: form.packageSha256.trim() || undefined,
    packageFileName: form.packageFileName.trim() || undefined,
    packageSize: form.packageSize,
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
      platform: query.platform || undefined,
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
    <div class="page-shell">
      <NAlert type="info" :bordered="false" class="page-hint">
        {{ t('version.listHint') }}
      </NAlert>
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
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
          <NSelect
            v-model:value="query.platform"
            :options="platformOptions"
            :placeholder="t('version.platform')"
            clearable
            style="width: 120px"
            @update:value="onSearch"
          />
          <NButton type="primary" @click="onSearch">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:version:create')"
          type="primary"
          @click="openCreate"
        >
          {{ t('version.create') }}
        </NButton>
      </NSpace>
      <NSpin :show="loading">
        <NDataTable
          :columns="columns"
          :data="items"
          :bordered="false"
          :single-line="false"
          :scroll-x="1200"
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
    </div>

    <AdminFormShell
      v-model:show="showForm"
      :title="formTitle"
      :width="560"
      :mask-closable="false"
    >
      <NAlert v-if="formReadonly" type="info" :bordered="false" class="readonly-hint">
        {{ t('version.publishedReadonlyHint') }}
      </NAlert>
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120">
        <NFormItem :label="t('version.currentVersion')" path="version">
          <NInput v-model:value="form.version" :placeholder="t('version.versionPh')" :disabled="formReadonly" />
        </NFormItem>
        <NFormItem :label="t('version.channel')" path="channel">
          <NSelect v-model:value="form.channel" :options="channelFormOptions" :disabled="formReadonly" />
        </NFormItem>
        <NFormItem :label="t('version.platform')" path="platform">
          <NSelect v-model:value="form.platform" :options="platformFormOptions" :disabled="formReadonly" />
        </NFormItem>
        <NFormItem :label="t('version.downloadUrl')">
          <NSpace vertical style="width: 100%">
            <NSpace v-if="!formReadonly">
              <NButton :loading="packageUploading" @click="pickPackage">
                {{ t('version.uploadPackage') }}
              </NButton>
              <span v-if="form.packageFileName" class="package-name">{{ form.packageFileName }}</span>
            </NSpace>
            <span v-else-if="form.packageFileName" class="package-name">{{ form.packageFileName }}</span>
            <NInput
              v-model:value="form.downloadUrl"
              :placeholder="t('version.downloadUrlPh')"
              :disabled="formReadonly"
            />
            <p v-if="!formReadonly" class="field-hint">{{ t('version.uploadPackageHint') }}</p>
            <p v-if="form.packageSha256" class="field-hint sha-hint">
              SHA-256: {{ form.packageSha256 }}
            </p>
          </NSpace>
          <input
            v-if="!formReadonly"
            ref="packageInputRef"
            type="file"
            accept=".exe,.msi,.dmg,.deb,.rpm,.AppImage,application/octet-stream"
            hidden
            @change="onPackageSelected"
          />
        </NFormItem>
        <NFormItem :label="t('setting.minSupportedVersion')">
          <NInput
            v-model:value="form.minSupportedVersion"
            :placeholder="t('version.versionPh')"
            :disabled="formReadonly"
          />
        </NFormItem>
        <NFormItem :label="t('setting.forceUpdate')">
          <NSwitch v-model:value="form.forceUpdate" :disabled="formReadonly" />
        </NFormItem>
        <NFormItem :label="t('version.releaseNotes')">
          <NInput
            v-model:value="form.releaseNotes"
            type="textarea"
            :rows="4"
            :placeholder="t('version.releaseNotesPh')"
            :disabled="formReadonly"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showForm = false">{{ formReadonly ? t('common.close') : t('common.cancel') }}</NButton>
          <NButton v-if="!formReadonly" type="primary" :loading="saving" @click="save">
            {{ t('common.save') }}
          </NButton>
        </NSpace>
      </template>
    </AdminFormShell>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}
.package-name {
  color: var(--n-text-color-2);
  font-size: 13px;
}
.field-hint {
  margin: 0;
  font-size: 12px;
  color: var(--n-text-color-3);
}
.readonly-hint {
  margin-bottom: 12px;
}
</style>
