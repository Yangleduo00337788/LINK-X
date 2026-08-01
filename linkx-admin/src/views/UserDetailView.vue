<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  approveUserDevice,
  banUser,
  freezeUser,
  getUser,
  listUserDevices,
  listUserLogins,
  resetUserPassword,
  revokeUserDevice,
  setUserDeviceBinding,
  unbanUser,
  unfreezeUser,
  updateUser,
  type AdminUserDetail,
  type DeviceItem,
  type UserLoginItem,
} from '@/api/users'
import { banDevice, kickDevice, unbanDevice } from '@/api/devices'
import { listDepts, type AdminDept } from '@/api/depts'
import { onAdminRealtimeEvent } from '@/api/realtime'
import { formatTime, displayOrNone, formatIp, userStatusLabel, userStatusType } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const user = ref<AdminUserDetail | null>(null)
const devices = ref<DeviceItem[]>([])
const deviceLoading = ref(false)
const logins = ref<UserLoginItem[]>([])
const loginLoading = ref(false)
const loginTotal = ref(0)
const loginQuery = reactive({ page: 1, size: 10 })

let offRealtime: (() => void) | null = null

const showEdit = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  nickname: '',
  email: '',
  phone: '',
  signature: '',
  deptId: 0 as number,
})

const deptOptions = ref<{ label: string; value: number }[]>([])

const showResetPassword = ref(false)
const resetSaving = ref(false)
const resetForm = reactive({
  newPassword: '',
  confirmPassword: '',
})
const generatedPassword = ref('')

const userId = computed(() => String(route.params.id || ''))

const regionText = computed(() => {
  void locale.value
  if (!user.value) return t('common.none')
  const parts = [user.value.country, user.value.province, user.value.region].filter(Boolean)
  return parts.length ? parts.join(' / ') : t('common.none')
})

const rolesText = computed(() => {
  void locale.value
  if (!user.value?.roles?.length) return t('common.none')
  return user.value.roles.join(', ')
})

const ADMIN_PORTAL_ROLES = new Set([
  'admin',
  'super_admin',
  'ops_admin',
  'audit_admin',
  'security_admin',
  'readonly_observer',
])

const canToggleStatus = computed(() => {
  if (!user.value) return false
  if (String(user.value.id) === String(auth.user?.id)) return false
  if (user.value.roles?.some((r) => ADMIN_PORTAL_ROLES.has(r))) return false
  return true
})

const canEdit = computed(() => {
  if (!auth.hasPermission('admin:user:edit') || !user.value) return false
  const isSelf = String(user.value.id) === String(auth.user?.id)
  const isPortalUser = user.value.roles?.some((r) => ADMIN_PORTAL_ROLES.has(r))
  return isSelf || !isPortalUser
})

const canResetPassword = computed(
  () => canToggleStatus.value && auth.hasPermission('admin:user:reset-password'),
)

const deviceColumns = computed<DataTableColumns<DeviceItem>>(() => {
  void locale.value
  return [
    { title: t('user.deviceId'), key: 'id', ellipsis: { tooltip: true } },
    { title: t('user.deviceName'), key: 'deviceName' },
    { title: t('user.deviceType'), key: 'deviceType', width: 100 },
    {
      title: t('device.onlineStatus'),
      key: 'online',
      width: 90,
      render: (row) =>
        h(
          NTag,
          { type: row.online ? 'success' : 'default', size: 'small' },
          () => (row.online ? t('device.online') : t('device.offline')),
        ),
    },
    {
      title: t('device.approved'),
      key: 'approved',
      width: 90,
      render: (row) =>
        h(
          NTag,
          { type: row.approved ? 'success' : 'warning', size: 'small' },
          () => (row.approved ? t('device.approved') : t('device.unapproved')),
        ),
    },
    {
      title: t('device.banned'),
      key: 'banned',
      width: 80,
      render: (row) =>
        row.banned
          ? h(NTag, { type: 'error', size: 'small' }, () => t('device.banned'))
          : '-',
    },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    {
      title: t('user.current'),
      key: 'current',
      width: 80,
      render: (row) =>
        row.current
          ? h(NTag, { type: 'success', size: 'small' }, () => t('common.yes'))
          : t('common.no'),
    },
    {
      title: t('user.lastActive'),
      key: 'lastActive',
      width: 170,
      render: (row) => formatTime(row.lastActive),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 260,
      render: (row) => {
        const buttons = []
        if (auth.hasPermission('admin:device:kick')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'warning',
                secondary: true,
                onClick: () => confirmKickDevice(row),
              },
              () => t('device.kick'),
            ),
          )
        }
        if (auth.hasPermission('admin:user:device-approve')) {
          if (row.approved) {
            buttons.push(
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  onClick: () => confirmRevokeDevice(row),
                },
                () => t('device.revoke'),
              ),
            )
          } else {
            buttons.push(
              h(
                NButton,
                {
                  size: 'tiny',
                  type: 'primary',
                  secondary: true,
                  onClick: () => confirmApproveDevice(row),
                },
                () => t('device.approve'),
              ),
            )
          }
        }
        if (row.banned && auth.hasPermission('admin:device:unban')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                secondary: true,
                onClick: () => confirmUnbanDevice(row),
              },
              () => t('device.unban'),
            ),
          )
        } else if (!row.banned && auth.hasPermission('admin:device:ban')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                secondary: true,
                onClick: () => confirmBanDevice(row),
              },
              () => t('device.ban'),
            ),
          )
        }
        return buttons.length ? h(NSpace, { size: 4 }, () => buttons) : null
      },
    },
  ]
})

function confirmKickDevice(row: DeviceItem) {
  dialog.warning({
    title: t('device.kickTitle'),
    content: t('device.kickConfirm', {
      user: user.value?.username || userId.value,
      device: row.deviceName || row.id,
    }),
    positiveText: t('device.kick'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await kickDevice(userId.value, row.id)
      message.success(t('device.kickSuccess'))
      await loadDevices()
    },
  })
}

function confirmBanDevice(row: DeviceItem) {
  dialog.error({
    title: t('device.banTitle'),
    content: t('device.banConfirm', {
      user: user.value?.username || userId.value,
      device: row.deviceName || row.id,
    }),
    positiveText: t('device.ban'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await banDevice(userId.value, row.id)
      message.success(t('device.banSuccess'))
      await loadDevices()
    },
  })
}

function confirmUnbanDevice(row: DeviceItem) {
  dialog.warning({
    title: t('device.unbanTitle'),
    content: t('device.unbanConfirm', {
      user: user.value?.username || userId.value,
      device: row.deviceName || row.id,
    }),
    positiveText: t('device.unban'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await unbanDevice(userId.value, row.id)
      message.success(t('device.unbanSuccess'))
      await loadDevices()
    },
  })
}

function confirmApproveDevice(row: DeviceItem) {
  dialog.warning({
    title: t('device.approve'),
    content: row.deviceName || row.id,
    positiveText: t('device.approve'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await approveUserDevice(userId.value, row.id)
      message.success(t('device.approveSuccess'))
      await loadDevices()
    },
  })
}

function confirmRevokeDevice(row: DeviceItem) {
  dialog.warning({
    title: t('device.revoke'),
    content: row.deviceName || row.id,
    positiveText: t('device.revoke'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await revokeUserDevice(userId.value, row.id)
      message.success(t('device.revokeSuccess'))
      await loadDevices()
    },
  })
}

async function toggleDeviceBinding(enabled: boolean) {
  dialog.warning({
    title: t('device.binding'),
    content: enabled ? t('device.bindingEnableConfirm') : t('device.bindingDisableConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await setUserDeviceBinding(userId.value, enabled)
      message.success(t('device.bindingSuccess'))
      await load()
      await loadDevices()
    },
  })
}
const loginColumns = computed<DataTableColumns<UserLoginItem>>(() => {
  void locale.value
  return [
    {
      title: t('loginLog.result'),
      key: 'success',
      width: 90,
      render: (row) =>
        h(
          NTag,
          { type: row.success === 1 ? 'success' : 'error', size: 'small' },
          () => (row.success === 1 ? t('user.loginSuccess') : t('user.loginFail')),
        ),
    },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    { title: t('loginLog.reason'), key: 'reason', ellipsis: { tooltip: true } },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
  ]
})

async function load() {
  loading.value = true
  try {
    user.value = await getUser(userId.value)
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  if (!auth.hasPermission('admin:user:device:list')) return
  deviceLoading.value = true
  try {
    devices.value = (await listUserDevices(userId.value)) || []
  } finally {
    deviceLoading.value = false
  }
}

async function loadLogins() {
  if (!auth.hasPermission('admin:user:login:list')) return
  loginLoading.value = true
  try {
    const data = await listUserLogins(userId.value, {
      page: loginQuery.page,
      size: loginQuery.size,
    })
    logins.value = data.items || []
    loginTotal.value = data.total || 0
  } finally {
    loginLoading.value = false
  }
}

function flattenDepts(nodes: AdminDept[], depth = 0, acc: { label: string; value: number }[] = []) {
  for (const d of nodes) {
    acc.push({ label: `${'  '.repeat(depth)}${d.name}`, value: d.id })
    if (d.children?.length) flattenDepts(d.children, depth + 1, acc)
  }
  return acc
}

async function loadDeptOptions() {
  const tree = (await listDepts()) || []
  deptOptions.value = [{ label: t('user.deptNone'), value: 0 }, ...flattenDepts(tree)]
}

function openEdit() {
  if (!user.value) return
  Object.assign(editForm, {
    nickname: user.value.nickname || '',
    email: user.value.email || '',
    phone: user.value.phone || '',
    signature: user.value.signature || '',
    deptId: user.value.deptId ?? 0,
  })
  showEdit.value = true
}

function openResetPassword() {
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
      userId.value,
      generate ? undefined : resetForm.newPassword,
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

async function submitEdit() {
  editSaving.value = true
  try {
    await updateUser(userId.value, {
      nickname: editForm.nickname.trim() || undefined,
      email: editForm.email.trim() || undefined,
      phone: editForm.phone.trim() || undefined,
      signature: editForm.signature.trim() || undefined,
      deptId: editForm.deptId ?? 0,
    })
    message.success(t('user.editSuccess'))
    showEdit.value = false
    await load()
  } finally {
    editSaving.value = false
  }
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

onMounted(async () => {
  await load()
  await Promise.all([loadDevices(), loadLogins(), loadDeptOptions()])
  offRealtime = onAdminRealtimeEvent((evt) => {
    if (evt?.type !== 'device_presence') return
    const eventUserId = String(evt.relatedId || evt.userId || '')
    const deviceId = typeof evt.deviceId === 'string' ? evt.deviceId : ''
    if (!deviceId || eventUserId !== userId.value) return
    const online = Boolean(evt.online)
    let matched = false
    devices.value = devices.value.map((row) => {
      if (row.id === deviceId) {
        matched = true
        return {
          ...row,
          online,
          lastActive: online ? new Date().toISOString() : row.lastActive,
        }
      }
      return row
    })
    if (online && !matched) {
      void loadDevices()
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
    <NSpin :show="loading">
      <div v-if="user" class="page-shell">
        <NSpace class="page-toolbar" justify="end">
          <NButton @click="router.back()">{{ t('common.back') }}</NButton>
          <NButton v-if="canEdit" @click="openEdit">{{ t('user.editProfile') }}</NButton>
          <NButton
            v-if="canResetPassword"
            type="warning"
            secondary
            @click="openResetPassword"
          >
            {{ t('user.resetPassword') }}
          </NButton>
          <NButton
            v-if="canToggleStatus && user?.status === 1 && auth.hasPermission('admin:user:freeze')"
            type="warning"
            secondary
            @click="confirmAction(t('user.freeze'), t('user.freezeConfirm'), () => freezeUser(userId))"
          >
            {{ t('user.freeze') }}
          </NButton>
          <NButton
            v-if="canToggleStatus && user?.status === 1 && auth.hasPermission('admin:user:ban')"
            type="error"
            secondary
            @click="confirmAction(t('user.ban'), t('user.banConfirm'), () => banUser(userId))"
          >
            {{ t('user.ban') }}
          </NButton>
          <NButton
            v-if="canToggleStatus && user?.status === 0 && auth.hasPermission('admin:user:unfreeze')"
            type="primary"
            secondary
            @click="confirmAction(t('user.unfreeze'), t('user.unfreezeConfirm'), () => unfreezeUser(userId))"
          >
            {{ t('user.unfreeze') }}
          </NButton>
          <NButton
            v-if="canToggleStatus && user?.status === 0 && auth.hasPermission('admin:user:unban')"
            secondary
            @click="confirmAction(t('user.unban'), t('user.unbanConfirm'), () => unbanUser(userId))"
          >
            {{ t('user.unban') }}
          </NButton>
        </NSpace>
        <NTabs type="line">
          <NTabPane name="profile" :tab="t('user.tabProfile')">
            <NDescriptions label-placement="left" :column="2" bordered>
              <NDescriptionsItem label="ID">{{ user.id }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.username')">{{ user.username }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.nickname')">{{ user.nickname || '-' }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('common.status')">
                <NTag :type="userStatusType(user.status)" size="small">{{ userStatusLabel(user.status) }}</NTag>
              </NDescriptionsItem>
              <NDescriptionsItem :label="t('user.email')">{{ displayOrNone(user.email) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.phone')">{{ displayOrNone(user.phone) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.gender')">{{ displayOrNone(user.gender) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.region')">{{ regionText }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.signature')" :span="2">{{ displayOrNone(user.signature) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.roles')" :span="2">{{ rolesText }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.dept')">{{ user.deptName || t('common.none') }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('device.binding')">
                <NSpace align="center">
                  <span>{{ user.deviceBindingEnabled ? t('device.bindingOn') : t('device.bindingOff') }}</span>
                  <NSwitch
                    v-if="auth.hasPermission('admin:user:device-binding')"
                    :value="!!user.deviceBindingEnabled"
                    @update:value="toggleDeviceBinding"
                  />
                </NSpace>
              </NDescriptionsItem>
              <NDescriptionsItem :label="t('common.createTime')">{{ formatTime(user.createTime) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('common.updateTime')">{{ formatTime(user.updateTime) }}</NDescriptionsItem>
            </NDescriptions>
          </NTabPane>
          <NTabPane
            v-if="auth.hasPermission('admin:user:device:list')"
            name="devices"
            :tab="t('user.tabDevices')"
          >
            <NDataTable :columns="deviceColumns" :data="devices" :loading="deviceLoading" />
          </NTabPane>
          <NTabPane
            v-if="auth.hasPermission('admin:user:login:list')"
            name="logins"
            :tab="t('user.tabLogins')"
          >
            <NDataTable
              :columns="loginColumns"
              :data="logins"
              :loading="loginLoading"
              :pagination="{
                page: loginQuery.page,
                pageSize: loginQuery.size,
                itemCount: loginTotal,
                showSizePicker: true,
                pageSizes: [10, 20, 50],
                onUpdatePage: (p: number) => { loginQuery.page = p; loadLogins() },
                onUpdatePageSize: (s: number) => { loginQuery.size = s; loginQuery.page = 1; loadLogins() },
              }"
              remote
            />
          </NTabPane>
        </NTabs>
      </div>
    </NSpin>

    <NModal v-model:show="showEdit" preset="card" :title="t('user.editTitle')" style="width: 480px">
      <NForm label-placement="left" label-width="80">
        <NFormItem :label="t('user.nickname')">
          <NInput v-model:value="editForm.nickname" :placeholder="t('user.nicknamePlaceholder')" />
        </NFormItem>
        <NFormItem :label="t('user.email')">
          <NInput v-model:value="editForm.email" :placeholder="t('user.emailPlaceholder')" />
        </NFormItem>
        <NFormItem :label="t('user.phone')">
          <NInput v-model:value="editForm.phone" :placeholder="t('user.phonePlaceholder')" />
        </NFormItem>
        <NFormItem :label="t('user.dept')">
          <NSelect
            v-model:value="editForm.deptId"
            :options="deptOptions"
            filterable
            :placeholder="t('user.deptPlaceholder')"
          />
        </NFormItem>
        <NFormItem :label="t('user.signature')">
          <NInput
            v-model:value="editForm.signature"
            type="textarea"
            :rows="3"
            :placeholder="t('user.signaturePlaceholder')"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showEdit = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="editSaving" @click="submitEdit">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="showResetPassword"
      preset="card"
      :title="t('user.resetPasswordTitle')"
      style="width: 480px"
      @after-leave="generatedPassword = ''"
    >
      <p style="margin: 0 0 12px; color: var(--n-text-color-3); line-height: 1.5">
        {{ t('user.resetPasswordHint') }}
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
      <NInput
        v-else
        :value="generatedPassword"
        type="textarea"
        :rows="2"
        readonly
        :placeholder="t('user.resetPasswordGenerated')"
      />
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
