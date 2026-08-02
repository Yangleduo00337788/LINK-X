<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NDropdown,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type DropdownOption,
  type SelectOption,
} from 'naive-ui'
import {
  banUser,
  exportUsers,
  freezeUser,
  listUsers,
  resetUserPassword,
  unbanUser,
  unfreezeUser,
  type AdminUserListItem,
} from '@/api/users'
import { listDepts, type AdminDept } from '@/api/depts'
import { formatTime, userStatusLabel, userStatusType } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const items = ref<AdminUserListItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '' as '' | number,
  deptId: null as number | null,
  range: null as [number, number] | null,
})
const deptOptions = ref<SelectOption[]>([])

const showResetPassword = ref(false)
const resetSaving = ref(false)
const resetTargetId = ref('')
const resetTargetName = ref('')
const resetForm = reactive({
  newPassword: '',
  confirmPassword: '',
})
const generatedPassword = ref('')

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('common.normal'), value: 1 },
    { label: t('common.frozen'), value: 0 },
  ]
})

const ADMIN_PORTAL_ROLES = new Set([
  'admin',
  'super_admin',
  'ops_admin',
  'audit_admin',
  'security_admin',
  'readonly_observer',
])

const PRIVILEGED_ADMIN_ROLES = new Set(['admin', 'super_admin'])

function isPrivilegedOperator() {
  return auth.user?.roles?.some((r) => PRIVILEGED_ADMIN_ROLES.has(r)) ?? false
}

function canToggleStatus(row: AdminUserListItem) {
  if (String(row.id) === String(auth.user?.id)) return false
  const isPortalUser = row.roles?.some((r) => ADMIN_PORTAL_ROLES.has(r))
  if (isPortalUser && !isPrivilegedOperator()) return false
  return true
}

function actionOptions(row: AdminUserListItem): DropdownOption[] {
  if (!canToggleStatus(row)) return []
  const opts: DropdownOption[] = []
  if (row.status === 1) {
    if (auth.hasPermission('admin:user:freeze')) {
      opts.push({ label: t('user.freeze'), key: 'freeze' })
    }
    if (auth.hasPermission('admin:user:ban')) {
      opts.push({ label: t('user.ban'), key: 'ban' })
    }
  } else {
    if (auth.hasPermission('admin:user:unfreeze')) {
      opts.push({ label: t('user.unfreeze'), key: 'unfreeze' })
    }
    if (auth.hasPermission('admin:user:unban')) {
      opts.push({ label: t('user.unban'), key: 'unban' })
    }
  }
  if (auth.hasPermission('admin:user:reset-password')) {
    opts.push({ label: t('user.resetPassword'), key: 'resetPassword' })
  }
  return opts
}

const columns = computed<DataTableColumns<AdminUserListItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    { title: t('user.username'), key: 'username', ellipsis: { tooltip: true } },
    { title: t('user.nickname'), key: 'nickname', ellipsis: { tooltip: true } },
    { title: t('user.email'), key: 'email', ellipsis: { tooltip: true } },
    {
      title: t('common.status'),
      key: 'status',
      width: 90,
      render: (row) =>
        h(NTag, { type: userStatusType(row.status), size: 'small' }, () =>
          userStatusLabel(row.status)
        ),
    },
    {
      title: t('user.roles'),
      key: 'roles',
      render: (row) => (row.roles?.length ? row.roles.join(', ') : t('common.none')),
    },
    {
      title: t('common.createTime'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (row) => {
        const opts = actionOptions(row)
        return h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'tiny', onClick: () => router.push(`/admin/users/${row.id}`) }, () =>
            t('common.detail')
          ),
          opts.length
            ? h(
                NDropdown,
                {
                  options: opts,
                  onSelect: (key: string) => handleAction(row, key),
                },
                () => h(NButton, { size: 'tiny' }, () => t('common.actions'))
              )
            : null,
        ])
      },
    },
  ]
})

function openResetPassword(row: AdminUserListItem) {
  resetTargetId.value = row.id
  resetTargetName.value = row.username || row.id
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  generatedPassword.value = ''
  showResetPassword.value = true
}

async function submitResetPassword(generate: boolean) {
  if (!generate) {
    if (!resetForm.newPassword) {
      message.warning(t('user.newPassword'))
      return
    }
    if (resetForm.newPassword !== resetForm.confirmPassword) {
      message.warning(t('user.passwordMismatch'))
      return
    }
  }
  resetSaving.value = true
  try {
    const result = await resetUserPassword(
      resetTargetId.value,
      generate ? undefined : resetForm.newPassword
    )
    message.success(t('user.resetPasswordSuccess'))
    if (result?.generated && result.temporaryPassword) {
      generatedPassword.value = result.temporaryPassword
    } else {
      showResetPassword.value = false
    }
  } finally {
    resetSaving.value = false
  }
}

function handleAction(row: AdminUserListItem, key: string) {
  if (key === 'resetPassword') {
    openResetPassword(row)
    return
  }
  const map: Record<string, { label: string; content: string; action: () => Promise<unknown> }> = {
    freeze: {
      label: t('user.freeze'),
      content: t('user.freezeConfirm'),
      action: () => freezeUser(row.id),
    },
    unfreeze: {
      label: t('user.unfreeze'),
      content: t('user.unfreezeConfirm'),
      action: () => unfreezeUser(row.id),
    },
    ban: {
      label: t('user.ban'),
      content: t('user.banConfirm'),
      action: () => banUser(row.id),
    },
    unban: {
      label: t('user.unban'),
      content: t('user.unbanConfirm'),
      action: () => unbanUser(row.id),
    },
  }
  const item = map[key]
  if (!item) return
  confirmAction(item.label, item.content, item.action)
}

function confirmAction(label: string, content: string, action: () => Promise<unknown>) {
  dialog.warning({
    title: t('common.confirmAction', { action: label }),
    content,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await action()
      message.success(t('common.actionSuccess', { action: label }))
      await load()
    },
  })
}

async function load() {
  loading.value = true
  try {
    const data = await listUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
      deptId: query.deptId ?? undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
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

async function doExport() {
  exporting.value = true
  try {
    await exportUsers({
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
      deptId: query.deptId ?? undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

function flattenDepts(nodes: AdminDept[], prefix = ''): { label: string; value: number }[] {
  const out: { label: string; value: number }[] = []
  for (const n of nodes) {
    const label = prefix ? `${prefix} / ${n.name}` : n.name
    out.push({ label, value: n.id })
    if (n.children?.length) {
      out.push(...flattenDepts(n.children, label))
    }
  }
  return out
}

async function loadDepts() {
  // 无部门权限时不请求，避免非特权角色进入用户列表即刷 403
  const allOpt = { label: t('user.allDepts'), value: null as unknown as number }
  if (!auth.hasPermission('admin:dept:list')) {
    deptOptions.value = [allOpt]
    return
  }
  try {
    const tree = await listDepts()
    deptOptions.value = [allOpt, ...flattenDepts(tree || [])]
  } catch {
    deptOptions.value = [allOpt]
  }
}

onMounted(() => {
  void loadDepts()
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
            :placeholder="t('user.searchPlaceholder')"
            width="240px"
            @search="search"
          />
          <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
          <NSelect
            v-if="auth.hasPermission('admin:dept:list')"
            v-model:value="query.deptId"
            :options="deptOptions"
            clearable
            style="width: 180px"
            :placeholder="t('user.allDepts')"
          />
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:user:export')"
          :loading="exporting"
          @click="doExport"
        >
          {{ t('common.export') }}
        </NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
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
        :scroll-x="1100"
        remote
      />
    </div>

    <NModal
      v-model:show="showResetPassword"
      preset="card"
      :title="t('user.resetPasswordTitle')"
      style="width: 480px"
      @after-leave="generatedPassword = ''"
    >
      <p style="margin: 0 0 12px; color: var(--n-text-color-3); line-height: 1.5">
        {{ t('user.resetPasswordHint') }}
        <template v-if="resetTargetName">（{{ resetTargetName }}）</template>
      </p>
      <NForm v-if="!generatedPassword" label-placement="left" label-width="90">
        <NFormItem :label="t('user.newPassword')">
          <NInput
            v-model:value="resetForm.newPassword"
            type="password"
            show-password-on="click"
            :placeholder="t('user.newPassword')"
          />
        </NFormItem>
        <NFormItem :label="t('user.confirmPassword')">
          <NInput
            v-model:value="resetForm.confirmPassword"
            type="password"
            show-password-on="click"
            :placeholder="t('user.confirmPassword')"
          />
        </NFormItem>
      </NForm>
      <NInput v-else :value="generatedPassword" type="textarea" :rows="2" readonly />
      <p v-if="generatedPassword" style="margin: 8px 0 0; color: var(--n-text-color-3)">
        {{ t('user.resetPasswordGenerated') }}
      </p>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showResetPassword = false">
            {{ generatedPassword ? t('common.close') : t('common.cancel') }}
          </NButton>
          <template v-if="!generatedPassword">
            <NButton :loading="resetSaving" @click="submitResetPassword(true)">
              {{ t('user.resetPasswordGenerate') }}
            </NButton>
            <NButton type="primary" :loading="resetSaving" @click="submitResetPassword(false)">
              {{ t('common.confirm') }}
            </NButton>
          </template>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
